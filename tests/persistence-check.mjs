import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
const record = JSON.parse(await readFile('output/test-results/persistence.json', 'utf8'));
const base = process.env.API_BASE || record.base;
let cookie = '';
async function call(path, data, csrf) {
  const response = await fetch(base + '/tijian' + path, {
    method: data === undefined ? 'GET' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookie,
      ...(csrf ? { 'X-CSRF-Token': csrf } : {}),
    },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  });
  const cookies = response.headers.getSetCookie();
  if (cookies.length) cookie = cookies.map((v) => v.split(';')[0]).join('; ');
  const json = await response.json();
  assert.equal(response.status, 200, json.message);
  assert.equal(json.code, 0, json.message);
  return json.data;
}
const session = await call('/auth/session');
assert.equal(session.user, null, '重启后应重新登录');
await call(
  '/users/getUsersByUserIdByPass',
  { userId: record.userId, password: 'Health123!' },
  session.csrfToken,
);
const order = await call('/orders/getOrdersById?orderId=' + record.orderId);
const report = await call('/reports/detail?orderId=' + record.orderId);
assert.equal(order.state, 2);
assert.ok(report.items.length && report.conclusions.length);
await writeFile(
  'output/test-results/persistence-result.json',
  JSON.stringify(
    { passed: true, orderId: order.orderId, state: order.state, date: new Date().toISOString() },
    null,
    2,
  ),
);
console.log('PASS 重启后订单、报告与总检结论均已保留');
