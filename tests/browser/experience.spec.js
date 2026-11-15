import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const origin = process.env.USER_ORIGIN || 'http://127.0.0.1:8081';
const manageOrigin = process.env.MANAGE_ORIGIN || 'http://127.0.0.1:8089';
let fixture;
test.beforeAll(async () => {
  fixture = JSON.parse(await readFile('output/test-results/experience-fixture.json', 'utf8'));
  if (!fixture.latestReportId)
    throw new Error('请先成功运行 node tests/api-experience.mjs，生成独立的两次归档报告。');
});
async function login(page) {
  await page.goto(origin + '/#/login');
  await page.getByLabel('手机号码', { exact: true }).fill(fixture.userId);
  await page.getByLabel('登录密码', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: '登 录', exact: true }).click();
  await expect(page).toHaveURL(/#\/index/);
  await expect(page.locator('.my-checkup')).toContainText('最新报告');
}
async function noOverflow(page) {
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
  ).toBeTruthy();
}

test('大字模式保留原背景、跨刷新保存，标准字号个人中心仍一屏完整显示', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  const title = page.locator('.my-checkup h2');
  const before = await title.evaluate((el) => parseFloat(getComputedStyle(el).fontSize));
  const background = await page
    .locator('.app-shell')
    .evaluate((el) => getComputedStyle(el).backgroundImage);
  await page.getByRole('button', { name: '切换为大字模式' }).click();
  expect(await title.evaluate((el) => parseFloat(getComputedStyle(el).fontSize))).toBeCloseTo(
    before * 1.16,
    1,
  );
  expect(
    await page.locator('.app-shell').evaluate((el) => getComputedStyle(el).backgroundImage),
  ).toBe(background);
  await page.reload();
  await expect(page.getByRole('button', { name: '切换为标准字号' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  for (const width of [320, 390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await noOverflow(page);
  }
  await page.getByRole('button', { name: '切换为标准字号' }).click();
  await page.goto(origin + '/#/personal');
  await expect(page.locator('.personal-menu-view')).toBeVisible();
  for (const [width, height] of [
    [390, 844],
    [360, 640],
    [320, 568],
    [1440, 900],
  ]) {
    await page.setViewportSize({ width, height });
    const layout = await page.evaluate(() => {
      const rows = [...document.querySelector('.personal-links').children];
      return {
        height: document.documentElement.scrollHeight,
        count: rows.length,
        last: rows.at(-1).textContent,
        bottom: rows.at(-1).getBoundingClientRect().bottom,
        footer: document.querySelector('.prototype-footer').getBoundingClientRect().top,
      };
    });
    expect(layout.height).toBeLessThanOrEqual(height + 1);
    expect(layout.count).toBe(9);
    expect(layout.last).toContain('退出登录');
    expect(layout.bottom).toBeLessThanOrEqual(layout.footer);
    await noOverflow(page);
  }
});

test('首页、机构指南、消息、真实趋势与报告区间图可在手机和电脑查看', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await login(page);
  await expect(page.locator('.my-checkup a[href^="#/report/"]')).toHaveAttribute(
    'href',
    `#/report/${fixture.latestReportId}`,
  );
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 950 });
    await page.goto(origin + `/#/orders/${fixture.orderId}`);
    await expect(page.locator('.checkup-guide')).toContainText('服务台核对预约');
    await noOverflow(page);
    await page.goto(origin + '/#/messages');
    await expect(page.locator('.message-card')).toHaveCount(6);
    await expect(page.getByRole('button', { name: '全部已读', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: '未读消息', exact: true }).click();
    await expect(page.getByText('暂无消息', { exact: true })).toBeVisible();
    await noOverflow(page);
    await page.goto(origin + `/#/trends?personId=${fixture.personId}`);
    await expect(page.locator('.trend-data-point')).toHaveCount(2);
    await page.locator('.trend-data-point').first().focus();
    await expect(page.locator('.trend-data-point').first()).toHaveClass(/selected/);
    await expect(page.locator('.trend-table-scroll tbody tr')).toHaveCount(2);
    await expect(page.locator('.trend-records a').first()).toHaveAttribute(
      'href',
      `#/report/${fixture.orderId}`,
    );
    await noOverflow(page);
    await page.goto(origin + `/#/report/${fixture.latestReportId}`);
    await page.getByRole('button', { name: '报告详情', exact: true }).click();
    await expect(page.locator('.metric-reference').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /打印.*保存 PDF/ })).toBeEnabled();
    await noOverflow(page);
  }
  expect(errors).toEqual([]);
});

test('医生介绍可进入、错误页面可返回，首页查询失败可重试', async ({ page }) => {
  await login(page);
  await page.locator('.home-doctors a').first().click();
  await expect(page.locator('.doctor-profile h1')).toBeVisible();
  await expect(page.getByRole('heading', { name: /服务内容/ })).toBeVisible();
  await noOverflow(page);
  await page.goto(origin + '/#/doctors/missing');
  await expect(page.getByRole('heading', { name: '暂未找到这位医生' })).toBeVisible();
  let fail = true;
  await page.route('**/tijian/dashboard', async (route) => {
    if (fail) {
      fail = false;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: '概览暂时无法加载', data: null }),
      });
    } else await route.continue();
  });
  await page.getByRole('link', { name: '返回首页', exact: true }).click();
  await expect(page.locator('.my-checkup')).toContainText('概览暂时无法加载');
  await page.locator('.my-checkup').getByRole('button', { name: '重新加载' }).click();
  await expect(page.locator('.my-checkup')).toContainText('最新报告');
});

test('医生工作台用真实数据筛选，管理员统计与机构指南可打开', async ({ page, browser }) => {
  await page.goto(manageOrigin + '/#/login');
  await page.getByPlaceholder('请输入医生工号或管理员账号').fill(fixture.docCode);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill('Doctor123!');
  await page.getByRole('button', { name: '登录工作台', exact: true }).click();
  await expect(page.locator('.workbench-cards')).toContainText('已归档');
  await expect(page.locator('.workbench-cards button').last().locator('strong')).toHaveText('2');
  await page.getByPlaceholder('手机号码', { exact: true }).fill('不存在的查询');
  const request = page.waitForRequest(
    (request) =>
      request.url().includes('/orders/listOrders?') &&
      new URL(request.url()).searchParams.get('state') === '2',
  );
  await page.locator('.workbench-cards button').last().click();
  const params = new URL((await request).url()).searchParams;
  expect(params.get('page')).toBe('1');
  expect(params.get('userId')).toBe('');
  await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(2);
  await page.goto(manageOrigin + '/#/statistics');
  await expect(page).toHaveURL(/#\/orders$/);
  const adminContext = await browser.newContext();
  try {
    const admin = await adminContext.newPage();
    await admin.goto(manageOrigin + '/#/login');
    await admin.getByRole('button', { name: '系统管理员', exact: true }).click();
    await admin.getByRole('button', { name: '登录工作台', exact: true }).click();
    await admin.goto(manageOrigin + '/#/statistics');
    await expect(admin.locator('.statistics-cards')).toBeVisible();
    await expect(admin.locator('.panel-title').filter({ hasText: '每日预约与归档' })).toBeVisible();
    for (const width of [390, 1440]) {
      await admin.setViewportSize({ width, height: 950 });
      await noOverflow(admin);
    }
    await admin.goto(manageOrigin + '/#/catalog/hospitals');
    await admin
      .getByRole('row')
      .filter({ hasText: '云医院-和平院区' })
      .getByRole('button', { name: '体检指南', exact: true })
      .click();
    const dialog = admin.getByRole('dialog');
    await expect(dialog.getByRole('button', { name: '保存体检指南' })).toBeEnabled();
    await expect(dialog.getByRole('textbox').first()).not.toHaveValue('');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await admin
      .getByRole('row')
      .filter({ hasText: fixture.hospitalName })
      .getByRole('button', { name: '体检指南', exact: true })
      .click();
    await expect(dialog).toContainText('该医院已停用，体检指南仅供查看。');
    await expect(dialog.getByRole('textbox').first()).toBeDisabled();
    await expect(dialog.getByRole('button', { name: '保存体检指南' })).toHaveCount(0);
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
  } finally {
    await adminContext.close();
  }
});
