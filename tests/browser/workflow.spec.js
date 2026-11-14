import { test, expect, request } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
const userOrigin = process.env.USER_ORIGIN || 'http://127.0.0.1:8081';
const manageOrigin = process.env.MANAGE_ORIGIN || 'http://127.0.0.1:8089';
const backend = process.env.API_BASE || 'http://127.0.0.1:18080';
function identity(serial) {
  const b = '11010119900101' + String(serial).padStart(3, '0');
  return (
    b +
    '10X98765432'[
      [...b].reduce(
        (s, n, i) => s + Number(n) * [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2][i],
        0,
      ) % 11
    ]
  );
}
async function fixture() {
  const client = await request.newContext();
  const response = await client.get(backend + '/tijian/auth/session');
  const token = (await response.json()).data.csrfToken;
  const phone = '137' + Date.now().toString().slice(-8),
    serial = 201 + 2 * (Date.now() % 300);
  const r = await client.post(backend + '/tijian/users/saveUsers', {
    headers: { 'X-CSRF-Token': token },
    data: {
      userId: phone,
      password: 'Health123!',
      realName: '浏览器测试用户',
      sex: 1,
      birthday: '1990-01-01',
      identityCard: identity(serial),
    },
  });
  expect(r.ok(), await r.text()).toBeTruthy();
  await client.dispose();
  return { phone, familyIdentity: identity(serial + 2) };
}
test('用户家属预约 → 医生保存归档 → 用户手机查看报告', async ({ browser }) => {
  await mkdir('output/screenshots', { recursive: true });
  const f = await fixture();
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(userOrigin + '/#/login');
  await page.getByLabel('手机号码', { exact: true }).fill(f.phone);
  await page.getByLabel('登录密码', { exact: true }).fill('Health123!');
  await page.getByRole('button', { name: '登 录' }).click();
  await expect(page).toHaveURL(/#\/index/);
  await page.reload();
  await expect(page.getByText('我的健康报告', { exact: true })).toBeVisible();
  await page.screenshot({ path: 'output/screenshots/user-home-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'output/screenshots/user-home-mobile.png', fullPage: true });
  await page.goto(userOrigin + '/#/persons');
  await page.getByRole('button', { name: /添加家人$/ }).click();
  const dialog = page.getByRole('dialog', { name: '健康档案' });
  await dialog.getByLabel('姓名', { exact: true }).fill('浏览器测试家属');
  await dialog.getByLabel('出生日期', { exact: true }).fill('1990-01-01');
  await dialog.getByLabel('身份证号码', { exact: false }).fill(f.familyIdentity);
  await dialog.getByRole('button', { name: '保存档案' }).click();
  await expect(dialog).not.toBeVisible();
  await page.goto(userOrigin + '/#/appointment');
  await expect(page.getByRole('button', { name: '家属预约', exact: true })).toBeVisible();
  await page.screenshot({ path: 'output/screenshots/appointment-mobile.png', fullPage: true });
  await page.getByRole('button', { name: '家属预约', exact: true }).click();
  await page.getByRole('button', { name: /浏览器测试家属/ }).click();
  await page.getByRole('button', { name: '选择医院' }).first().click();
  await page.getByRole('button', { name: /^详情/ }).first().click();
  await expect(page.locator('.meal-details')).toBeVisible();
  await page.getByRole('button', { name: '选择套餐' }).first().click();
  let available = page.locator('.calendar-day:not([disabled])');
  if (!(await available.count())) {
    await page.getByRole('button', { name: '下个月', exact: true }).click();
  }
  await available.first().click();
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  await page.getByRole('checkbox').check();
  await page.screenshot({
    path: 'output/screenshots/booking-confirmation-desktop.png',
    fullPage: true,
  });
  await page.getByRole('button', { name: '确认支付', exact: true }).click();
  await expect(page).toHaveURL(/appointmentsuccess\/\d+/);
  const orderId = page.url().match(/appointmentsuccess\/(\d+)/)[1];
  const staffContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  });
  const staff = await staffContext.newPage();
  staff.on('pageerror', (e) => errors.push(e.message));
  await staff.goto(manageOrigin + '/#/login');
  await staff.getByRole('button', { name: '体检医生', exact: true }).click();
  await staff.getByRole('button', { name: /登录工作台/ }).click();
  await expect(staff).toHaveURL(/#\/orders/);
  await staff.goto(manageOrigin + '/#/orders/' + orderId);
  await expect(staff.locator('.manual-check-group').first()).toBeVisible();
  const groups = staff.locator('.manual-check-group');
  for (let i = 0; i < (await groups.count()); i++) {
    const group = groups.nth(i);
    const fields = group.locator('.manual-check-field');
    const values = {
      收缩压: '145',
      舒张压: '80',
      身高: '175',
      体重: '72',
      白细胞计数: '5.6',
      血红蛋白: '140',
    };
    for (let j = 0; j < (await fields.count()); j++) {
      const field = fields.nth(j);
      const name = (await field.locator('label[for]').textContent()).trim();
      await field.getByRole('textbox').fill(values[name] || '未见明显异常');
    }
    await group.getByRole('button', { name: /项保存$/ }).click();
    await expect(group.locator('.unsaved-note')).toHaveCount(0);
  }
  const conclusion = staff.locator('#conclusion-form');
  await conclusion.getByLabel('总检结论项标题').fill('课程演示总检结论');
  await conclusion
    .getByLabel('总检结论项内容')
    .fill('本报告为虚构测试数据，建议保持规律生活并定期复查。');
  await conclusion.getByRole('button', { name: '添加', exact: true }).click();
  await expect(staff.getByRole('cell', { name: '课程演示总检结论', exact: true })).toBeVisible();
  await staff.reload();
  await expect(staff.locator('.manual-check-group').first()).toBeVisible();
  await staff.screenshot({ path: 'output/screenshots/doctor-report-desktop.png', fullPage: true });
  await staff.getByRole('button', { name: '体检套餐总检结果报告归档' }).click();
  await staff.getByRole('button', { name: '确认归档', exact: true }).click();
  await expect(staff.getByText('此报告已归档，内容为只读，用户端可查看。')).toBeVisible();
  await page.goto(userOrigin + '/#/report/' + orderId);
  await page.reload();
  await expect(page.getByText(/课程演示总检结论/).first()).toBeVisible();
  await page.screenshot({ path: 'output/screenshots/user-report-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'output/screenshots/user-report-mobile.png', fullPage: true });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
  ).toBeTruthy();
  await page.goto(userOrigin + '/#/persons');
  await expect(page.getByText('浏览器测试家属', { exact: true })).toBeVisible();
  await page.screenshot({ path: 'output/screenshots/family-profiles-mobile.png', fullPage: true });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
  ).toBeTruthy();
  expect(errors).toEqual([]);
  await staffContext.close();
  await context.close();
});
test('管理员各管理模块加载及手机登录布局', async ({ page }) => {
  await page.goto(manageOrigin + '/#/login');
  await page.getByRole('button', { name: '系统管理员', exact: true }).click();
  await page.getByRole('button', { name: /登录工作台/ }).click();
  await expect(page).toHaveURL(/#\/orders/);
  for (const route of [
    '/catalog/hospitals',
    '/catalog/setmeals',
    '/catalog/checkitems',
    '/catalog/doctors',
    '/associations',
    '/schedules',
  ]) {
    await page.goto(manageOrigin + '/#' + route);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
    await expect(page.getByText('请求失败', { exact: true })).toHaveCount(0);
  }
  await page.screenshot({ path: 'output/screenshots/admin-schedules-desktop.png', fullPage: true });
  const mobile = await page
    .context()
    .browser()
    .newContext({ viewport: { width: 390, height: 844 } });
  const login = await mobile.newPage();
  await login.goto(userOrigin + '/#/login');
  await expect(login.getByLabel('手机号码', { exact: true })).toBeVisible();
  expect(
    await login.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
  ).toBeTruthy();
  await login.screenshot({ path: 'output/screenshots/user-login-mobile.png', fullPage: true });
  await mobile.close();
});

test('个人资料、密码修改与重新登录', async ({ page }) => {
  const f = await fixture();
  await page.goto(userOrigin + '/#/login');
  await page.getByLabel('手机号码', { exact: true }).fill(f.phone);
  await page.getByLabel('登录密码', { exact: true }).fill('Health123!');
  await page.getByRole('button', { name: '登 录' }).click();
  await expect(page).toHaveURL(/#\/index/);
  await page.goto(userOrigin + '/#/personal');
  await page.getByRole('button', { name: '账号资料', exact: true }).click();
  await page.getByLabel('真实姓名', { exact: true }).fill('资料更新测试用户');
  const saved = page.waitForResponse((r) => r.url().endsWith('/users/updateProfile'));
  await page.getByRole('button', { name: '保存个人资料' }).click();
  expect((await saved).status()).toBe(200);
  await page.getByRole('button', { name: '修改密码', exact: true }).click();
  await page.getByLabel('当前密码', { exact: true }).fill('Health123!');
  await page.getByLabel('新密码', { exact: true }).fill('NewHealth456!');
  await page.getByLabel('确认新密码', { exact: true }).fill('NewHealth456!');
  const changed = page.waitForResponse((r) => r.url().endsWith('/users/changePassword'));
  await page.getByRole('button', { name: '更新登录密码' }).click();
  expect((await changed).status()).toBe(200);
  await page.getByRole('button', { name: /返回个人中心/ }).click();
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/#\/login/);
  await page.getByLabel('手机号码', { exact: true }).fill(f.phone);
  await page.getByLabel('登录密码', { exact: true }).fill('NewHealth456!');
  await page.getByRole('button', { name: '登 录' }).click();
  await expect(page).toHaveURL(/#\/index/);
  await page.goto(userOrigin + '/#/personal');
  await expect(
    page.locator('.personal-info').getByText('资料更新测试用户', { exact: true }),
  ).toBeVisible();
});
