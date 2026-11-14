/**
 * 真实 MySQL 黑盒并发验收：node tests/api-races.mjs
 * 每次创建独立的虚构测试医院、套餐、医生和用户，不修改演示基础资料。
 * API_BASE 可覆盖默认后端地址。结果始终写入 output/test-results/race-results.json。
 */
import assert from 'node:assert/strict';
import { randomInt, randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';

const base = (process.env.API_BASE || 'http://127.0.0.1:18080').replace(/\/$/, '');
const runId = `${Date.now().toString(36)}${randomInt(10000).toString(36)}`;
const results = [];
const fixture = {};
const reportPath = 'output/test-results/race-results.json';
const password = 'RaceTest123!';

function day(offset) {
  // 业务统一为 Asia/Shanghai，避免测试机时区不同时跨日。
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
      .formatToParts(new Date())
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, part.value]),
  );
  const date = new Date(
    Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day) + offset),
  );
  return date.toISOString().slice(0, 10);
}

function identity(seed = randomInt(1_000_000)) {
  const year = 1980 + (Math.floor(seed / 10000) % 35);
  const month = 1 + (Math.floor(seed / 400) % 12);
  const date = 1 + (Math.floor(seed / 20) % 28);
  const birthday = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`;
  const sequence = String((seed % 499) * 2 + 1).padStart(3, '0');
  const body = `110101${birthday.replaceAll('-', '')}${sequence}`;
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const checksum = [...body].reduce((sum, digit, i) => sum + Number(digit) * weights[i], 0) % 11;
  return { birthday, sex: 1, identityCard: body + '10X98765432'[checksum] };
}

class Client {
  constructor(prefix = '/tijian') {
    this.prefix = prefix;
    this.cookies = new Map();
    this.csrfToken = '';
  }

  async request(path, body) {
    const response = await fetch(`${base}${this.prefix}${path}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(this.cookies.size
          ? { Cookie: [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ') }
          : {}),
        ...(body !== undefined && this.csrfToken ? { 'X-CSRF-Token': this.csrfToken } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(30000),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0];
      const separator = pair.indexOf('=');
      this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
    const text = await response.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      throw new Error(`${path} 返回非 JSON (${response.status}): ${text.slice(0, 180)}`);
    }
    return { status: response.status, code: json.code, message: json.message, data: json.data };
  }

  async expect(path, body, expected = 200) {
    const response = await this.request(path, body);
    assert.equal(
      response.status,
      expected,
      `${path}: HTTP ${response.status}, ${response.message}`,
    );
    if (expected === 200) assert.equal(response.code, 0, `${path}: ${response.message}`);
    return response.data;
  }

  async init() {
    const session = await this.expect('/auth/session');
    assert.ok(session.csrfToken, '服务端必须发放 CSRF 令牌');
    this.csrfToken = session.csrfToken;
    return session;
  }

  async staffLogin(docCode, pass) {
    await this.init();
    await this.expect('/doctor/getDoctorByCodeByPass', { docCode, password: pass });
    await this.init();
    return this;
  }

  async register(examinee, label) {
    await this.init();
    for (let attempt = 0; attempt < 4; attempt++) {
      const userId = `137${String(randomInt(100_000_000)).padStart(8, '0')}`;
      const response = await this.request('/users/saveUsers', {
        userId,
        password,
        realName: `并发测试${label}`,
        ...examinee,
      });
      if (response.status === 409) continue;
      assert.equal(
        response.status,
        200,
        `注册虚构用户失败: ${response.status} ${response.message}`,
      );
      assert.equal(response.code, 0);
      await this.init();
      this.userId = userId;
      const persons = await this.expect('/persons/list');
      this.person = persons.find((person) => person.isSelf === true || person.isSelf === 1);
      assert.ok(this.person?.personId, '注册后必须生成本人档案');
      return this;
    }
    throw new Error('多次尝试后仍无法创建测试账号');
  }
}

async function test(name, action) {
  const started = Date.now();
  try {
    const evidence = await action();
    results.push({ name, passed: true, milliseconds: Date.now() - started, evidence });
    console.log(`PASS ${name}`);
  } catch (error) {
    results.push({ name, passed: false, milliseconds: Date.now() - started, error: error.message });
    console.error(`FAIL ${name}: ${error.message}`);
    process.exitCode = 1;
  }
}

function assertOneWinner(responses) {
  assert.deepEqual(
    responses.map((response) => response.status).sort((a, b) => a - b),
    [200, 409],
    `并发请求必须只有一个成功、另一个冲突: ${JSON.stringify(responses.map(({ status, message }) => ({ status, message })))}`,
  );
  assert.equal(responses.find((response) => response.status === 200).code, 0);
}

function values(report, value) {
  return report.items
    .flatMap((group) => group.details)
    .map((detail) => ({ cidrId: detail.cidrId, value: String(value), isError: 0 }));
}

async function main() {
  const admin = await new Client('/tijiancms').staffLogin('admin', 'Admin123!');
  const hospital = await admin.expect('/admin/hospitals/save', {
    name: `并发验收医院-${runId}`,
    picture: '/img/hospita1.png',
    telephone: '4000000000',
    address: '虚构并发测试地址',
    businessHours: '周一至周日 07:30-15:30',
    deadline: '采血截止10:30',
    state: 1,
  });
  const checkitem = await admin.expect('/admin/checkitems/save', {
    ciName: `并发验收检查-${runId}`,
    ciContent: '单数值结果，用于检验并发写入原子性',
    meaning: '虚构课程测试资料',
    remarks: '不用于真实诊疗',
    state: 1,
    details: [
      {
        name: '并发验证指标',
        unit: 'U',
        minrange: 0,
        maxrange: 200,
        normalValue: null,
        normalValueString: '',
        type: 1,
        required: true,
      },
    ],
  });
  const meal = await admin.expect('/admin/setmeals/save', {
    name: `并发验收套餐-${runId}`,
    type: 2,
    price: 123.45,
    state: 1,
    ciIds: [checkitem.ciId],
  });
  await admin.expect('/admin/hospital-setmeals/save', { hpId: hospital.hpId, smIds: [meal.smId] });
  await admin.expect('/admin/schedules/save', {
    hpId: hospital.hpId,
    from: day(1),
    to: day(4),
    capacity: 8,
    weekdays: [1, 2, 3, 4, 5, 6, 7],
  });
  const doctors = [];
  for (const suffix of ['A', 'B']) {
    const doctor = await admin.expect('/admin/doctors/save', {
      docCode: `R${runId}${suffix}`,
      realName: `并发医生${suffix}`,
      password,
      sex: 1,
      deptno: 1,
      hpId: hospital.hpId,
      role: 'DOCTOR',
      state: 1,
    });
    doctors.push(await new Client('/tijiancms').staffLogin(doctor.docCode, password));
  }
  const [doctorA, doctorB] = doctors;
  const identitySeed = randomInt(1_000_000);
  const users = [];
  for (let index = 0; index < 3; index++) {
    users.push(
      await new Client().register(identity(identitySeed + index * 1001), `场景${index + 1}`),
    );
  }
  const sharedIdentity = identity(identitySeed + 4004);
  users.push(await new Client().register(sharedIdentity, '同人账号甲'));
  users.push(await new Client().register(sharedIdentity, '同人账号乙'));
  Object.assign(fixture, {
    hospitalId: hospital.hpId,
    setmealId: meal.smId,
    checkitemId: checkitem.ciId,
    dates: [day(1), day(2), day(3), day(4)],
  });

  async function book(user, date) {
    return user.expect('/orders/saveOrders', {
      personId: user.person.personId,
      hpId: hospital.hpId,
      smId: meal.smId,
      orderDate: date,
      requestId: randomUUID(),
    });
  }
  async function read(orderId) {
    return doctorA.expect(`/ciReport/listCiReport?orderId=${orderId}`);
  }
  async function used(date) {
    const rows = await admin.expect(
      `/admin/schedules?hpId=${hospital.hpId}&from=${date}&to=${date}`,
    );
    assert.equal(rows.length, 1, '专用测试日期应有且只有一行名额记录');
    return Number(rows[0].used);
  }

  await test('两医生同版本并发保存：一成功一冲突且结果不被覆盖', async () => {
    const order = await book(users[0], day(1));
    const before = await read(order.orderId);
    const responses = await Promise.all([
      doctorA.request('/ciDetailedReport/updateCiDetailedReport', {
        orderId: order.orderId,
        version: before.order.reportVersion,
        items: values(before, 101),
      }),
      doctorB.request('/ciDetailedReport/updateCiDetailedReport', {
        orderId: order.orderId,
        version: before.order.reportVersion,
        items: values(before, 102),
      }),
    ]);
    assertOneWinner(responses);
    const after = await read(order.orderId);
    const winnerValue = responses[0].status === 200 ? '101' : '102';
    assert.equal(Number(after.order.reportVersion), Number(before.order.reportVersion) + 1);
    assert.equal(after.items[0].details[0].value, winnerValue, '落败医生不能覆盖获胜医生结果');
    assert.equal(Number(after.order.state), 1);
    assert.equal(await used(day(1)), 1);
    return {
      orderId: order.orderId,
      statuses: responses.map((response) => response.status),
      value: winnerValue,
      version: after.order.reportVersion,
      used: 1,
    };
  });

  await test('结果保存与取消竞争：最终状态、报告和名额保持一致', async () => {
    const user = users[1];
    const order = await book(user, day(2));
    const before = await read(order.orderId);
    const responses = await Promise.all([
      doctorA.request('/ciDetailedReport/updateCiDetailedReport', {
        orderId: order.orderId,
        version: before.order.reportVersion,
        items: values(before, 150),
      }),
      user.request('/orders/removeOrders', { orderId: order.orderId }),
    ]);
    assertOneWinner(responses);
    const after = await read(order.orderId);
    if (responses[1].status === 200) {
      assert.equal(Number(after.order.state), 3);
      assert.ok(
        after.items.every((group) =>
          group.details.every((detail) => !String(detail.value ?? '').trim()),
        ),
        '取消获胜时不得留下录入结果',
      );
      assert.ok(after.order.reportStarted === false || after.order.reportStarted === 0);
      assert.equal(await used(day(2)), 0);
      await user.expect('/orders/removeOrders', { orderId: order.orderId });
      assert.equal(await used(day(2)), 0, '重复取消不能再次释放名额');
      await doctorB.expect(
        '/ciDetailedReport/updateCiDetailedReport',
        { orderId: order.orderId, version: after.order.reportVersion, items: values(after, 151) },
        409,
      );
    } else {
      assert.equal(Number(after.order.state), 1);
      assert.equal(after.items[0].details[0].value, '150');
      assert.ok(after.order.reportStarted === true || after.order.reportStarted === 1);
      await user.expect('/orders/removeOrders', { orderId: order.orderId }, 409);
      assert.equal(await used(day(2)), 1, '录入获胜时取消不得释放名额');
    }
    assert.equal(Number(after.order.reportVersion), Number(before.order.reportVersion) + 1);
    return {
      orderId: order.orderId,
      statuses: responses.map((response) => response.status),
      finalState: after.order.state,
      version: after.order.reportVersion,
      used: await used(day(2)),
    };
  });

  await test('归档与旧版本保存竞争：单次获胜且归档后永远只读', async () => {
    const user = users[2];
    const order = await book(user, day(3));
    let report = await read(order.orderId);
    report = await doctorA.expect('/ciDetailedReport/updateCiDetailedReport', {
      orderId: order.orderId,
      version: report.order.reportVersion,
      items: values(report, 100),
    });
    report = await doctorA.expect('/overallResult/saveOverallResult', {
      orderId: order.orderId,
      version: report.order.reportVersion,
      title: '并发验收结论',
      content: '虚构检查结果，仅用于检验归档事务和版本控制。',
    });
    const version = report.order.reportVersion;
    const responses = await Promise.all([
      doctorA.request('/orders/updateOrdersState', { orderId: order.orderId, version }),
      doctorB.request('/ciDetailedReport/updateCiDetailedReport', {
        orderId: order.orderId,
        version,
        items: values(report, 160),
      }),
    ]);
    assertOneWinner(responses);
    let after = await read(order.orderId);
    assert.equal(Number(after.order.reportVersion), Number(version) + 1);
    if (responses[0].status === 200) {
      assert.equal(Number(after.order.state), 2);
      assert.equal(after.items[0].details[0].value, '100', '先归档时旧版本保存不能改变报告');
    } else {
      assert.equal(Number(after.order.state), 1);
      assert.equal(after.items[0].details[0].value, '160');
      after = await doctorA.expect('/orders/updateOrdersState', {
        orderId: order.orderId,
        version: after.order.reportVersion,
      });
      assert.equal(Number(after.order.state), 2);
    }
    const archivedValue = after.items[0].details[0].value;
    const archivedVersion = after.order.reportVersion;
    await doctorB.expect(
      '/ciDetailedReport/updateCiDetailedReport',
      { orderId: order.orderId, version: archivedVersion, items: values(after, 199) },
      409,
    );
    await doctorB.expect(
      '/overallResult/saveOverallResult',
      {
        orderId: order.orderId,
        version: archivedVersion,
        title: '应拒绝',
        content: '归档后禁止新增结论。',
      },
      409,
    );
    await user.expect('/orders/removeOrders', { orderId: order.orderId }, 409);
    after = await read(order.orderId);
    assert.equal(Number(after.order.state), 2);
    assert.equal(after.items[0].details[0].value, archivedValue);
    assert.equal(after.order.reportVersion, archivedVersion);
    const published = await user.expect(`/reports/detail?orderId=${order.orderId}`);
    assert.equal(Number(published.order.state), 2);
    assert.equal(await used(day(3)), 1, '归档订单仍占用预约日名额');
    return {
      orderId: order.orderId,
      statuses: responses.map((response) => response.status),
      finalState: 2,
      value: archivedValue,
      version: archivedVersion,
      used: 1,
    };
  });

  await test('跨账号同证件同时下单：仅一个有效订单且仅占一个名额', async () => {
    const pair = users.slice(3);
    const responses = await Promise.all(
      pair.map((user) =>
        user.request('/orders/saveOrders', {
          personId: user.person.personId,
          hpId: hospital.hpId,
          smId: meal.smId,
          orderDate: day(4),
          requestId: randomUUID(),
        }),
      ),
    );
    assertOneWinner(responses);
    const winnerIndex = responses.findIndex((response) => response.status === 200);
    const winner = responses[winnerIndex].data;
    const active = await Promise.all(
      pair.map((user) => user.expect('/orders/listOrdersByUserId?state=1')),
    );
    assert.deepEqual(
      active.map((orders) => orders.length),
      winnerIndex === 0 ? [1, 0] : [0, 1],
    );
    assert.equal(await used(day(4)), 1, '唯一约束失败的事务必须回滚名额占用');
    const management = await admin.expect(
      `/orders/listOrders?page=1&pageSize=20&hpId=${hospital.hpId}&orderDate=${day(4)}&state=1`,
    );
    assert.equal(Number(management.total), 1);
    assert.equal(management.items[0].orderId, winner.orderId);
    await pair[1 - winnerIndex].expect(
      `/orders/getOrdersById?orderId=${winner.orderId}`,
      undefined,
      403,
    );
    return {
      orderId: winner.orderId,
      statuses: responses.map((response) => response.status),
      activeCounts: active.map((orders) => orders.length),
      used: 1,
    };
  });
}

try {
  await main();
} catch (error) {
  process.exitCode = 1;
  results.push({ name: '并发验收环境与独立测试资料初始化', passed: false, error: error.message });
  console.error(error.stack);
}
await mkdir('output/test-results', { recursive: true });
await writeFile(
  reportPath,
  JSON.stringify(
    {
      runId,
      generatedAt: new Date().toISOString(),
      base,
      fictionalFixturesOnly: true,
      fixture,
      passed: results.filter((result) => result.passed).length,
      total: results.length,
      results,
    },
    null,
    2,
  ),
);
console.log(
  `${results.filter((result) => result.passed).length}/${results.length} race checks passed; ${reportPath}`,
);
