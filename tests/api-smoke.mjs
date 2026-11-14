import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const base = process.env.API_BASE || 'http://127.0.0.1:18080';
const results = [];
const stamp = Date.now().toString().slice(-8);
const day = (offset) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
function identity(serial) {
  const body = '11010119900101' + String(serial).padStart(3, '0');
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  return body + '10X98765432'[[...body].reduce((s, n, i) => s + Number(n) * weights[i], 0) % 11];
}
class Client {
  constructor(prefix = '/tijian') {
    this.prefix = prefix;
    this.cookie = '';
    this.csrf = '';
  }
  async request(path, body, expected = 200, prefix = this.prefix, csrf = true) {
    const response = await fetch(base + prefix + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(this.cookie ? { Cookie: this.cookie } : {}),
        ...(csrf && this.csrf ? { 'X-CSRF-Token': this.csrf } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const cookie = response.headers.getSetCookie?.() || [];
    if (cookie.length) this.cookie = cookie.map((v) => v.split(';')[0]).join('; ');
    const raw = await response.text();
    let json;
    try {
      json = JSON.parse(raw);
    } catch {
      throw new Error(`${path} 非JSON ${response.status}: ${raw.slice(0, 200)}`);
    }
    const accepted = Array.isArray(expected) ? expected : [expected];
    assert.ok(
      accepted.includes(response.status),
      `${path} expected ${accepted}, got ${response.status}: ${JSON.stringify(json)}`,
    );
    if (response.ok) assert.equal(json.code, 0, `${path}: ${json.message}`);
    return json.data;
  }
  async init() {
    const s = await this.request('/auth/session');
    this.csrf = s.csrfToken;
    return s;
  }
  async register(index, serial) {
    await this.init();
    return this.request('/users/saveUsers', {
      userId: `139${String(Number(stamp) + index)
        .padStart(8, '0')
        .slice(-8)}`,
      password: 'Health123!',
      realName: `接口测试${index}`,
      sex: 1,
      birthday: '1990-01-01',
      identityCard: identity(serial),
    });
  }
}
async function check(name, run) {
  const started = Date.now();
  try {
    const data = await run();
    results.push({ name, passed: true, ms: Date.now() - started });
    console.log(`PASS ${name}`);
    return data;
  } catch (error) {
    results.push({ name, passed: false, error: error.message });
    throw error;
  }
}

try {
  const admin = new Client('/tijiancms');
  await admin.init();
  await admin.request('/doctor/getDoctorByCodeByPass', { docCode: 'admin', password: 'Admin123!' });
  const anonymous = new Client();
  await check('匿名访问与CSRF校验', async () => {
    await anonymous.request('/orders/listOrdersByUserId', undefined, 401);
    await anonymous.request(
      '/users/getUsersByUserIdByPass',
      { userId: '13800000001', password: 'Health123!' },
      403,
      undefined,
      false,
    );
  });
  const serialBase = 101 + 2 * (Number(stamp) % 300);
  const a = new Client(),
    b = new Client(),
    c = new Client();
  await check('注册、登录态与证件脱敏', async () => {
    const u = await a.register(1, serialBase);
    await b.register(2, serialBase + 2);
    await c.register(3, serialBase + 4);
    assert.ok(!u.password && !u.identityCard);
    assert.ok(u.identityCardMasked);
    const me = await a.request('/users/me');
    assert.ok(me.userId.startsWith('139'));
  });
  const checks = await admin.request('/admin/checkitems');
  assert.ok(checks.length);
  const hp = await admin.request('/admin/hospitals/save', {
    name: `自动化测试医院${stamp}`,
    picture: '/img/hospita1.png',
    telephone: '4000000000',
    address: '虚构测试路1号',
    businessHours: '周一至周日 07:30-15:30',
    deadline: '10:30',
    state: 1,
  });
  const meal = await admin.request('/admin/setmeals/save', {
    name: `自动化基础套餐${stamp}`,
    type: 2,
    price: 299,
    state: 1,
    ciIds: checks.filter((x) => x.state === 1).map((x) => x.ciId),
  });
  await admin.request('/admin/hospital-setmeals/save', { hpId: hp.hpId, smIds: [meal.smId] });
  await admin.request('/admin/schedules/save', {
    hpId: hp.hpId,
    from: day(1),
    to: day(10),
    capacity: 4,
    weekdays: [1, 2, 3, 4, 5, 6, 7],
  });
  const doc = await admin.request('/admin/doctors/save', {
    docCode: `T${stamp}`,
    realName: '测试医生',
    password: 'Doctor123!',
    sex: 1,
    deptno: 1,
    hpId: hp.hpId,
    role: 'DOCTOR',
    state: 1,
  });
  const doctor = new Client('/tijiancms');
  await doctor.init();
  await doctor.request('/doctor/getDoctorByCodeByPass', {
    docCode: doc.docCode,
    password: 'Doctor123!',
  });
  const outsider = new Client('/tijiancms');
  await outsider.init();
  await outsider.request('/doctor/getDoctorByCodeByPass', {
    docCode: 'D002',
    password: 'Doctor123!',
  });
  const person = await a.request('/persons/save', {
    realName: '同一虚构家属',
    sex: 1,
    birthday: '1990-01-01',
    identityCard: identity(serialBase + 6),
    relationship: '父亲',
  });
  const otherPerson = await b.request('/persons/save', {
    realName: '同一虚构家属',
    sex: 1,
    birthday: '1990-01-01',
    identityCard: identity(serialBase + 6),
    relationship: '父亲',
  });
  let order;
  await check('真实下单、金额防篡改与重复提交', async () => {
    const request = {
      personId: person.personId,
      hpId: hp.hpId,
      smId: meal.smId,
      orderDate: day(2),
      requestId: randomUUID(),
      amount: 0.01,
    };
    const same = await Promise.all([
      a.request('/orders/saveOrders', request),
      a.request('/orders/saveOrders', request),
    ]);
    order = same[0];
    assert.equal(order.orderId, same[1].orderId);
    assert.equal(Number(order.amount), 299);
  });
  await check('跨账号重复体检人与越权拦截', async () => {
    await b.request(
      '/orders/saveOrders',
      {
        personId: otherPerson.personId,
        hpId: hp.hpId,
        smId: meal.smId,
        orderDate: day(3),
        requestId: randomUUID(),
      },
      409,
    );
    await b.request(`/orders/getOrdersById?orderId=${order.orderId}`, undefined, [403, 404]);
    await outsider.request(
      `/ciReport/listCiReport?orderId=${order.orderId}`,
      undefined,
      [403, 404],
    );
    await doctor.request('/admin/hospitals', undefined, 403);
    await a.request(`/reports/detail?orderId=${order.orderId}`, undefined, [403, 404, 409]);
  });
  const report = await doctor.request(`/ciReport/listCiReport?orderId=${order.orderId}`);
  await check('模板幂等及历史快照', async () => {
    const again = await doctor.request('/ciReport/createReportTemplate', {
      orderId: order.orderId,
    });
    assert.deepEqual(again.items, report.items);
    await admin.request('/admin/setmeals/save', {
      ...meal,
      name: meal.name + '新版',
      price: 999,
      ciIds: checks.filter((x) => x.state === 1).map((x) => x.ciId),
    });
    const old = await a.request(`/orders/getOrdersById?orderId=${order.orderId}`);
    assert.equal(Number(old.amount), 299);
    assert.equal(old.setmealName, meal.name);
    assert.deepEqual(
      (await doctor.request(`/ciReport/listCiReport?orderId=${order.orderId}`)).items,
      report.items,
    );
  });
  // 后续新订单继续使用有完整检查项的套餐。
  await admin.request('/admin/setmeals/save', {
    ...meal,
    ciIds: checks.filter((x) => x.state === 1).map((x) => x.ciId),
  });
  await check('名额容量不可低于占用', () =>
    admin.request(
      '/admin/schedules/save',
      { hpId: hp.hpId, from: day(2), to: day(2), capacity: 0, weekdays: [1, 2, 3, 4, 5, 6, 7] },
      [400, 409],
    ),
  );
  await check('未完成报告不能归档', () =>
    doctor.request(
      '/orders/updateOrdersState',
      { orderId: order.orderId, version: report.order.reportVersion },
      [400, 409],
    ),
  );
  let filled;
  await check('报告录入、后端异常计算与版本冲突', async () => {
    const values = report.items
      .flatMap((item) => item.details)
      .map((d, i) => ({
        cidrId: d.cidrId,
        value:
          d.type === 1
            ? String(
                i === 0
                  ? Number(d.maxrange ?? 100) + 10
                  : (Number(d.minrange ?? 0) + Number(d.maxrange ?? 100)) / 2,
              )
            : d.type === 2
              ? String(d.normalValue ?? 0)
              : '未见明显异常',
        isError: 0,
      }));
    filled = await doctor.request('/ciDetailedReport/updateCiDetailedReport', {
      orderId: order.orderId,
      version: report.order.reportVersion,
      items: values,
    });
    assert.ok(filled.order.reportStarted);
    assert.ok(filled.order.reportVersion > report.order.reportVersion);
    if (report.items[0]?.details[0]?.type === 1)
      assert.equal(filled.items[0].details[0].isError, 1);
    await doctor.request(
      '/ciDetailedReport/updateCiDetailedReport',
      { orderId: order.orderId, version: report.order.reportVersion, items: values },
      409,
    );
    await a.request('/orders/removeOrders', { orderId: order.orderId }, 409);
  });
  await check('结论增改删、归档与用户报告查看', async () => {
    filled = await doctor.request('/overallResult/saveOverallResult', {
      orderId: order.orderId,
      version: filled.order.reportVersion,
      title: '总检结论',
      content: '本报告为虚构课程测试数据。',
    });
    const conclusion = filled.conclusions[0];
    filled = await doctor.request('/overallResult/saveOverallResult', {
      orderId: order.orderId,
      version: filled.order.reportVersion,
      orId: conclusion.orId,
      title: '总检结论',
      content: '建议定期复查（虚构演示数据）。',
    });
    filled = await doctor.request('/overallResult/saveOverallResult', {
      orderId: order.orderId,
      version: filled.order.reportVersion,
      title: '临时结论',
      content: '将删除',
    });
    const temp = filled.conclusions.find((x) => x.title === '临时结论');
    filled = await doctor.request('/overallResult/removeOverallResult', {
      orderId: order.orderId,
      version: filled.order.reportVersion,
      orId: temp.orId,
    });
    filled = await doctor.request('/orders/updateOrdersState', {
      orderId: order.orderId,
      version: filled.order.reportVersion,
    });
    assert.equal(filled.order.state, 2);
    const published = await a.request(`/reports/detail?orderId=${order.orderId}`);
    assert.equal(published.order.state, 2);
    assert.equal(published.conclusions.length, 1);
    await doctor.request(
      '/overallResult/saveOverallResult',
      {
        orderId: order.orderId,
        version: filled.order.reportVersion,
        title: '禁止修改',
        content: '归档只读',
      },
      409,
    );
  });
  await check('归档后再次预约与重复取消只释放一次', async () => {
    const repeat = await a.request('/orders/saveOrders', {
      personId: person.personId,
      hpId: hp.hpId,
      smId: meal.smId,
      orderDate: day(3),
      requestId: randomUUID(),
    });
    await Promise.all([
      a.request('/orders/removeOrders', { orderId: repeat.orderId }),
      a.request('/orders/removeOrders', { orderId: repeat.orderId }),
    ]);
    assert.equal((await a.request(`/orders/getOrdersById?orderId=${repeat.orderId}`)).state, 3);
    const date = new Date(day(3) + 'T12:00:00');
    const calendar = await a.request(
      `/calendar/listAppointmentCalendar?hpId=${hp.hpId}&year=${date.getFullYear()}&month=${date.getMonth() + 1}`,
    );
    assert.equal(calendar.find((x) => x.date === day(3)).used, 0);
  });
  await check('最后一个名额并发争抢', async () => {
    await admin.request('/admin/schedules/save', {
      hpId: hp.hpId,
      from: day(4),
      to: day(4),
      capacity: 1,
      weekdays: [1, 2, 3, 4, 5, 6, 7],
    });
    const bp = (await b.request('/persons/list')).find((p) => p.isSelf);
    const cp = (await c.request('/persons/list')).find((p) => p.isSelf);
    const outcomes = await Promise.allSettled([
      b.request('/orders/saveOrders', {
        personId: bp.personId,
        hpId: hp.hpId,
        smId: meal.smId,
        orderDate: day(4),
        requestId: randomUUID(),
      }),
      c.request('/orders/saveOrders', {
        personId: cp.personId,
        hpId: hp.hpId,
        smId: meal.smId,
        orderDate: day(4),
        requestId: randomUUID(),
      }),
    ]);
    assert.equal(outcomes.filter((o) => o.status === 'fulfilled').length, 1);
    const winner = outcomes[0].status === 'fulfilled' ? b : c;
    const win = outcomes.find((o) => o.status === 'fulfilled').value;
    await winner.request('/orders/removeOrders', { orderId: win.orderId });
  });
  await check('同浏览器两端身份独立退出', async () => {
    await a.init();
    await a.request(
      '/doctor/getDoctorByCodeByPass',
      { docCode: 'D001', password: 'Doctor123!' },
      200,
      '/tijiancms',
    );
    await a.request('/auth/logout', {}, 200, '/tijiancms');
    assert.ok((await a.request('/users/me')).userId);
  });
  await mkdir('output/test-results', { recursive: true });
  await writeFile(
    'output/test-results/persistence.json',
    JSON.stringify(
      { base, orderId: order.orderId, userId: (await a.request('/users/me')).userId },
      null,
      2,
    ),
  );
} catch (error) {
  console.error(error.stack);
  process.exitCode = 1;
}
await mkdir('output/test-results', { recursive: true });
await writeFile(
  'output/test-results/api-results.json',
  JSON.stringify({ date: new Date().toISOString(), base, results }, null, 2),
);
console.log(`${results.filter((r) => r.passed).length}/${results.length} checks passed`);
