import { test, expect } from '@playwright/test';

const origin = process.env.USER_ORIGIN || 'http://127.0.0.1:8081';
async function login(page) {
  await page.goto(origin + '/#/login');
  await page.getByLabel('手机号码', { exact: true }).fill('13800000001');
  await page.getByLabel('登录密码', { exact: true }).fill('Health123!');
  await page.getByRole('button', { name: '登 录', exact: true }).click();
  await expect(page).toHaveURL(/#\/index/);
  await page.reload();
  await expect(page.getByText('我的健康报告', { exact: true })).toBeVisible();
}
async function enterMeals(page) {
  await page.goto(origin + '/#/appointment');
  await expect(page.getByRole('button', { name: '确认，尚未进行' })).toBeDisabled();
  await page.getByRole('button', { name: '本人预约', exact: true }).click();
  await page.locator('.person-select').first().click();
  await page
    .getByRole('button', { name: /^选择医院 / })
    .first()
    .click();
  await expect(page).toHaveURL(/#\/setmeal/);
  await expect(page.locator('.booking-progress .current')).toContainText('套餐');
}

test('原背景、资讯详情及无效文章在手机和电脑均可访问', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await login(page);
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 950 });
    await page.goto(origin + '/#/index');
    await page.locator('.home-information a').first().click();
    await expect(page).toHaveURL(/#\/articles\/lung-nodules/);
    await expect(page.locator('.article-heading h1')).toContainText('肺结节');
    await expect(page.locator('.article-sources a')).toHaveCount(2);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
    ).toBeTruthy();
    await page.locator('.article-related a').click();
    await expect(page).toHaveURL(/#\/articles\/thyroid-nodules/);
    await expect(page.locator('.article-heading h1')).toContainText('甲状腺');
  }
  await page.goto(origin + '/#/articles/missing');
  await expect(page.getByRole('heading', { name: '暂未找到这篇资讯' })).toBeVisible();
  await expect(page.getByRole('link', { name: '返回首页' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('五步预约与套餐对比支持失败重试、差异和直接选择', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await enterMeals(page);
  const compare = page.getByRole('button', { name: /查看对比/ });
  await expect(compare).toBeDisabled();
  await page
    .getByRole('button', { name: /^加入对比 / })
    .first()
    .click();
  await expect(compare).toBeDisabled();
  await page
    .getByRole('button', { name: /^加入对比 / })
    .last()
    .click();
  await expect(compare).toBeEnabled();
  let failOnce = true;
  await page.route('**/tijian/setmeal/getSetmealById?**', async (route) => {
    if (failOnce) {
      failOnce = false;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: '本次详情加载失败', data: null }),
      });
    } else await route.continue();
  });
  await compare.click();
  const dialog = page.getByRole('dialog', { name: '体检套餐对比' });
  await expect(dialog.getByRole('button', { name: '重新加载' })).toBeVisible();
  await dialog.getByRole('button', { name: '重新加载' }).click();
  await expect(dialog.locator('.comparison-difference').first()).toBeVisible();
  expect(
    await dialog.locator('.comparison-scroll').evaluate((el) => el.scrollWidth > el.clientWidth),
  ).toBeTruthy();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
  ).toBeTruthy();
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(compare).toBeFocused();
  await compare.click();
  await dialog
    .getByRole('button', { name: /^预约此套餐 / })
    .first()
    .click();
  await expect(page).toHaveURL(/#\/selectdate/);
  await expect(page.locator('.booking-progress .current')).toContainText('日期');
  await page.getByRole('button', { name: '返回医院步骤' }).click();
  await page
    .getByRole('button', { name: /^选择医院 / })
    .nth(1)
    .click();
  await expect(page.getByRole('button', { name: /查看对比（0）/ })).toBeDisabled();
});

test('报告打印包含完整结果和结论，不受屏幕标签影响', async ({ page }) => {
  await login(page);
  await page.goto(origin + '/#/reportlist');
  await page.locator('a[href^="#/report/"]').first().click();
  const print = page.getByRole('button', { name: /打印.*保存 PDF/ });
  await expect(print).toBeEnabled();
  const copy = page.locator('.report-print-root');
  await expect(copy).toContainText('检查结果');
  await expect(copy).toContainText('总检结论与建议');
  const summary = await copy.textContent();
  await page.getByRole('button', { name: '报告详情', exact: true }).click();
  expect(await copy.textContent()).toBe(summary);
  await page.evaluate(() => {
    window.__printed = 0;
    window.print = () => window.__printed++;
  });
  await print.click();
  await expect.poll(() => page.evaluate(() => window.__printed)).toBe(1);
  await page.emulateMedia({ media: 'print' });
  await expect(copy).toBeVisible();
  await expect(page.locator('#app')).not.toBeVisible();
  await page.pdf({
    path: 'output/test-results/round1-report.pdf',
    format: 'A4',
    preferCSSPageSize: true,
  });
  await page.emulateMedia({ media: 'screen' });
  await expect(copy).not.toBeVisible();
  await expect(print).toBeVisible();
});
