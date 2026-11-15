/** 本轮功能黑盒验收。创建独立虚构资料，保留报告供本地预览，不修改既有账号。 */
import assert from 'node:assert/strict';
import { randomInt, randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';

const base = process.env.API_BASE || 'http://127.0.0.1:18080';
const stamp = Date.now().toString(36);
const results = [],
  fixture = {};
const password = 'Health123!';
const day = (offset) => {
  const date = new Date(Date.now() + offset * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
};
function identity() {
  const birthday = `${1980 + randomInt(35)}-${String(1 + randomInt(12)).padStart(2, '0')}-${String(1 + randomInt(28)).padStart(2, '0')}`;
  const body =
    '110101' + birthday.replaceAll('-', '') + String(randomInt(499) * 2 + 1).padStart(3, '0');
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  return {
    birthday,
    sex: 1,
    identityCard:
      body + '10X98765432'[[...body].reduce((sum, v, i) => sum + Number(v) * weights[i], 0) % 11],
  };
}
class Client {
  constructor(prefix = '/tijian') {
    this.prefix = prefix;
    this.cookies = new Map();
    this.csrf = '';
  }
  async req(path, body, expected = 200, csrf = true) {
    const response = await fetch(base + this.prefix + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '),
        ...(csrf && this.csrf ? { 'X-CSRF-Token': this.csrf } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(30000),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0],
        i = pair.indexOf('=');
      this.cookies.set(pair.slice(0, i), pair.slice(i + 1));
    }
    const json = await response.json();
    assert.ok(
      (Array.isArray(expected) ? expected : [expected]).includes(response.status),
      `${path}: HTTP${response.status} ${json.message}`,
    );
    if (response.ok) assert.equal(json.code, 0, json.message);
    return json.data;
  }
  async init() {
    this.csrf = (await this.req('/auth/session')).csrfToken;
    return this;
  }
  async staff(code, pass) {
    await this.init();
    await this.req('/doctor/getDoctorByCodeByPass', { docCode: code, password: pass });
    await this.init();
    return this;
  }
  async register(name) {
    await this.init();
    const userId = `137${randomInt(10000000, 99999999)}`;
    await this.req('/users/saveUsers', { userId, password, realName: name, ...identity() });
    await this.init();
    return userId;
  }
}
async function check(name, run) {
  try {
    await run();
    results.push({ name, passed: true });
    console.log(`PASS ${name}`);
  } catch (error) {
    results.push({ name, passed: false, error: error.message });
    throw error;
  }
}
try {
  const admin = await new Client('/tijiancms').staff('admin', 'Admin123!');
  const a = new Client(),
    b = new Client();
  fixture.userId = await a.register('功能体验用户');
  fixture.password = password;
  await b.register('隔离验收用户');
  const person = (await a.req('/persons/list')).find((p) => p.isSelf);
  fixture.personId = person.personId;
  const hp = await admin.req('/admin/hospitals/save', {
    name: `功能验收机构-${stamp}`,
    picture: '/img/hospita1.png',
    telephone: '0373-9999',
    address: '健康路8号',
    businessHours: '周一至周日 07:30-15:30',
    deadline: '10:30',
    state: 1,
  });
  const checks = await admin.req('/admin/checkitems');
  // 只引用课程种子检查项，避免把其他并发验收脚本建立的专属项目带入预览报告。
  const checkIds = checks
    .filter((c) => c.state === 1 && [1, 2, 3].includes(c.ciId))
    .map((c) => c.ciId);
  assert.equal(checkIds.length, 3, '请先启用三项课程基础检查资料');
  const meal = await admin.req('/admin/setmeals/save', {
    name: `健康检查-${stamp}`,
    type: 2,
    price: 229.5,
    state: 1,
    ciIds: checkIds,
  });
  await admin.req('/admin/hospital-setmeals/save', { hpId: hp.hpId, smIds: [meal.smId] });
  await admin.req('/admin/schedules/save', {
    hpId: hp.hpId,
    from: day(1),
    to: day(5),
    capacity: 3,
    weekdays: [1, 2, 3, 4, 5, 6, 7],
  });
  fixture.docCode = `E${stamp}`;
  await admin.req('/admin/doctors/save', {
    docCode: fixture.docCode,
    password: 'Doctor123!',
    realName: '功能验收医生',
    sex: 1,
    deptno: 1,
    hpId: hp.hpId,
    role: 'DOCTOR',
    state: 1,
  });
  const doctor = await new Client('/tijiancms').staff(fixture.docCode, 'Doctor123!');
  const outsider = await new Client('/tijiancms').staff('D002', 'Doctor123!');
  fixture.hpId = hp.hpId;
  fixture.smId = meal.smId;
  fixture.hospitalName = hp.name;
  await check('新增接口拒绝未登录、非管理员及跨账号访问', async () => {
    const anon = new Client();
    for (const path of [
      '/dashboard',
      '/notifications',
      `/reports/trends?personId=${person.personId}`,
    ])
      await anon.req(path, undefined, 401);
    await doctor.req('/admin/statistics', undefined, 403);
    await doctor.req(`/admin/hospital-guidance?hpId=${hp.hpId}`, undefined, 403);
    await b.req(`/reports/trends?personId=${person.personId}`, undefined, 404);
  });
  const request = {
    personId: person.personId,
    hpId: hp.hpId,
    smId: meal.smId,
    orderDate: day(1),
    requestId: randomUUID(),
  };
  const first = await a.req('/orders/saveOrders', request);
  fixture.orderId = first.orderId;
  await check('重复预约只生成一条通知，归档前不显示趋势', async () => {
    assert.equal((await a.req('/orders/saveOrders', request)).orderId, first.orderId);
    const list = await a.req('/notifications');
    assert.equal(list.total, 1);
    assert.equal(list.unreadCount, 1);
    assert.equal(list.items[0].type, 'BOOKED');
    assert.equal((await a.req(`/reports/trends?personId=${person.personId}`)).series.length, 0);
    assert.equal((await a.req('/dashboard')).appointment.orderId, first.orderId);
  });
  await check('机构指南维护、空白回退及订单归属校验', async () => {
    const guide = {
      hpId: hp.hpId,
      preparation: '携带身份证件和预约信息。\n请提前联系机构确认检查安排。',
      arrivalSteps: '服务台核对预约\n按指引完成检查\n等待报告归档',
    };
    await admin.req('/admin/hospital-guidance/save', guide);
    assert.equal(
      (await a.req(`/orders/guidance?orderId=${first.orderId}`)).arrivalSteps,
      guide.arrivalSteps,
    );
    await b.req(`/orders/guidance?orderId=${first.orderId}`, undefined, [403, 404]);
    await admin.req(
      '/admin/hospital-guidance/save',
      { hpId: hp.hpId, preparation: 'x'.repeat(2001), arrivalSteps: '' },
      400,
    );
    const defaults = await admin.req('/admin/hospital-guidance/save', {
      hpId: hp.hpId,
      preparation: '',
      arrivalSteps: '',
    });
    assert.ok(defaults.preparation.length && defaults.arrivalSteps.length);
    await admin.req('/admin/hospital-guidance/save', guide);
  });
  await check('医生待办与筛选严格限定所属医院', async () => {
    const work = await doctor.req(`/workbench?hpId=2`);
    assert.equal(work.pendingEntry, 1);
    assert.equal(work.pendingArchive, 0);
    const list = await doctor.req('/orders/listOrders?workStage=unstarted');
    assert.equal(list.total, 1);
    assert.equal(list.items[0].orderId, first.orderId);
    await doctor.req('/orders/listOrders?workStage=invalid', undefined, 400);
    const other = await outsider.req(`/orders/listOrders?hpId=${hp.hpId}`);
    assert.ok(other.items.every((order) => order.hpId !== hp.hpId));
  });
  async function complete(order, index) {
    let report = await doctor.req(`/ciReport/listCiReport?orderId=${order.orderId}`);
    const items = report.items
      .flatMap((item) => item.details)
      .map((detail) => ({
        cidrId: detail.cidrId,
        value:
          Number(detail.type) === 1
            ? String(
                index === 1
                  ? Number(detail.maxrange ?? 100) + 1
                  : (Number(detail.minrange ?? 0) + Number(detail.maxrange ?? 100)) / 2,
              )
            : Number(detail.type) === 2
              ? String(detail.normalValue ?? 0)
              : { 身高: '175', 体重: '72' }[detail.name] || '未见明显异常',
        isError: 0,
      }));
    report = await doctor.req('/ciDetailedReport/updateCiDetailedReport', {
      orderId: order.orderId,
      version: report.order.reportVersion,
      items,
    });
    assert.equal((await doctor.req('/workbench')).pendingArchive, 1);
    report = await doctor.req('/overallResult/saveOverallResult', {
      orderId: order.orderId,
      version: report.order.reportVersion,
      title: '总检结论',
      content: '本报告为课程测试记录，用于验证指标展示及归档流程。',
    });
    await doctor.req('/orders/updateOrdersState', {
      orderId: order.orderId,
      version: report.order.reportVersion,
    });
  }
  await check('报告录入归档生成通知并更新工作台', async () => {
    await complete(first, 0);
    const work = await doctor.req('/workbench');
    assert.equal(work.pendingEntry, 0);
    assert.equal(work.pendingArchive, 0);
    assert.equal(work.archivedCount, 1);
    assert.equal(
      (await a.req('/notifications')).items.filter((m) => m.type === 'ARCHIVED').length,
      1,
    );
  });
  const second = await a.req('/orders/saveOrders', {
    ...request,
    orderDate: day(2),
    requestId: randomUUID(),
  });
  await complete(second, 1);
  fixture.latestReportId = second.orderId;
  await check('同一体检人两次归档结果组成真实趋势并保留原报告链接', async () => {
    const trend = await a.req(`/reports/trends?personId=${person.personId}`);
    assert.ok(trend.series.length > 0);
    assert.ok(trend.series.some((s) => s.points.length === 2));
    assert.ok(
      trend.series.every((s) =>
        s.points.every((p) => [first.orderId, second.orderId].includes(p.orderId)),
      ),
    );
    assert.equal((await a.req('/dashboard')).latestReport.orderId, second.orderId);
  });
  const third = await a.req('/orders/saveOrders', {
    ...request,
    orderDate: day(3),
    requestId: randomUUID(),
  });
  await check('重复取消只生成一次通知并恢复名额', async () => {
    await Promise.all([
      a.req('/orders/removeOrders', { orderId: third.orderId }),
      a.req('/orders/removeOrders', { orderId: third.orderId }),
    ]);
    const messages = await a.req('/notifications');
    assert.equal(messages.total, 6);
    assert.equal(messages.items.filter((m) => m.type === 'CANCELLED').length, 1);
    const statistics = await admin.req(
      `/admin/statistics?from=${day(1)}&to=${day(5)}&hpId=${hp.hpId}`,
    );
    assert.deepEqual(
      Object.fromEntries(
        ['appointments', 'archived', 'cancelled', 'active', 'capacity', 'used'].map((key) => [
          key,
          statistics.totals[key],
        ]),
      ),
      { appointments: 3, archived: 2, cancelled: 1, active: 0, capacity: 15, used: 2 },
    );
    assert.equal(Number(statistics.totals.archiveRate), 100);
    assert.equal(statistics.daily.length, 5);
    assert.equal(statistics.popularMeals[0].appointments, 2);
    assert.equal(Number(statistics.popularMeals[0].amount), 459);
    await admin.req('/admin/statistics?from=2020-01-01&to=2026-01-01', undefined, 400);
  });
  await check('消息已读受账号与 CSRF 保护，分页与已读状态跨会话保持', async () => {
    const list = await a.req('/notifications?page=1&pageSize=2');
    assert.equal(list.items.length, 2);
    assert.equal(list.total, 6);
    await b.req('/notifications/read', { id: list.items[0].id }, [403, 404]);
    await a.req('/notifications/read', { id: list.items[0].id }, 403, false);
    await a.req('/notifications/read', { id: list.items[0].id });
    await a.req('/notifications/read', { id: list.items[0].id });
    assert.equal((await a.req('/notifications?unreadOnly=true')).total, 5);
    await a.req('/notifications/read-all', {});
    const fresh = await new Client().init();
    await fresh.req('/users/getUsersByUserIdByPass', { userId: fixture.userId, password });
    assert.equal((await fresh.req('/notifications')).unreadCount, 0);
  });
  // 仅关闭本脚本新建的机构与套餐，历史报告与指南继续可读，避免污染可预约列表。
  await admin.req('/admin/hospitals/save', { ...hp, state: 2 });
  await admin.req('/admin/setmeals/save', {
    ...meal,
    ciIds: checkIds,
    state: 2,
  });
} catch (error) {
  console.error(error.stack);
  process.exitCode = 1;
}
await mkdir('output/test-results', { recursive: true });
await writeFile('output/test-results/experience-fixture.json', JSON.stringify(fixture, null, 2));
await writeFile(
  'output/test-results/experience-api.json',
  JSON.stringify({ date: new Date().toISOString(), results }, null, 2),
);
console.log(`${results.filter((r) => r.passed).length}/${results.length} checks passed`);
