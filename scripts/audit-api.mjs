// Uses two disposable accounts; never reads or changes an existing user's data.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const base = process.env.AUDIT_URL || 'http://localhost:8080';
const month = '2026-09';
const stamp = randomUUID();
const password = randomUUID();
const users = [0, 1].map(i => ({ email: `audit-${stamp}-${i}@example.invalid`, cookie: '' }));
let checks = 0;
async function request(user, path, method = 'GET', payload, headers = {}) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(user?.cookie ? { Cookie: user.cookie } : {}), ...headers },
    ...(payload !== undefined ? { body: JSON.stringify(payload) } : {})
  });
  const cookie = response.headers.get('set-cookie');
  if (cookie && user) user.cookie = cookie.split(';')[0];
  const text = await response.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data, headers: response.headers };
}
function status(result, expected, label) {
  assert.equal(result.status, expected, `${label}: ${JSON.stringify(result.data)}`);
  checks++; console.log(`PASS ${label}`);
}
const [a, b] = users;
const transaction = { type: 'EXPENSE', category: 'Food', amount: '250.50', description: 'Audit lunch <img src=x onerror=alert(1)>', date: `${month}-02` };
try {
  status(await request(null, '/api'), 401, 'anonymous dashboard rejected');
  status(await request(null, '/auth/login', 'POST', {}, { 'Content-Type': 'text/plain' }), 415, 'non-JSON writes rejected');
  status(await request(null, '/api', 'POST', transaction), 401, 'anonymous write rejected');
  status(await request(null, '/auth/register', 'POST', { name: null, email: a.email, password, monthlyBudget: 0 }), 400, 'null name rejected');
  for (const user of users) status(await request(user, '/auth/register', 'POST', { name: 'Audit Test', email: user.email, password, monthlyBudget: '45000' }), 200, 'registration with initial budget');
  const initial = await request(a, `/api?month=${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`);
  assert.equal(initial.data.profile.monthlyBudget, 45000); checks++;
  const duplicate = await request(null, '/auth/register', 'POST', { name: 'Duplicate', email: a.email, password, monthlyBudget: 0 });
  assert.ok([400, 409].includes(duplicate.status)); checks++;
  status(await request(null, '/auth/login', 'POST', { email: a.email, password: 'wrong-password' }), 400, 'wrong password rejected');
  status(await request(a, '/api?month=invalid', 'POST', transaction), 400, 'invalid month rejected before insert');
  let result = await request(a, `/api?month=${month}`);
  assert.equal(result.data.transactions.length, 0, 'Invalid month must not insert'); checks++;
  for (const amount of ['-1', '0', '0.001', '100000001', null]) status(await request(a, `/api?month=${month}`, 'POST', { ...transaction, amount }), 400, `reject invalid amount ${amount}`);
  status(await request(a, `/api?month=${month}`, 'POST', { ...transaction, date: '2026-02-30' }), 400, 'invalid date rejected');
  status(await request(a, `/api?month=${month}`, 'POST', { ...transaction, description: null }), 400, 'null description rejected');
  status(await request(a, `/api?month=${month}`, 'POST', transaction, { Origin: 'https://untrusted.invalid' }), 403, 'cross-origin write rejected');
  result = await request(a, `/api?month=${month}`, 'POST', transaction);
  status(result, 201, 'expense persisted');
  const id = result.data.transactions[0].id;
  assert.equal(result.data.expenses, 250.5); checks++;
  result = await request(a, `/api?month=${month}`, 'POST', { ...transaction, type: 'INCOME', category: 'Salary', amount: '1000.00' });
  status(result, 201, 'income persisted');
  assert.equal(result.data.balance, 749.5); checks++;
  assert.equal((await request(b, `/api?month=${month}`)).data.transactions.length, 0); checks++;
  status(await request(b, `/api/transactions/${id}?month=${month}`, 'PUT', transaction), 400, 'other user cannot edit');
  status(await request(b, `/api/transactions/${id}?month=${month}`, 'DELETE'), 400, 'other user cannot delete');
  status(await request(a, `/api/transactions/${id}?month=invalid`, 'DELETE'), 400, 'invalid month rejected before delete');
  assert.equal((await request(a, `/api?month=${month}`)).data.transactions.length, 2); checks++;
  status(await request(a, `/api/transactions/${id}?month=${month}`, 'PUT', { ...transaction, amount: '99.99', date: '2026-10-01' }), 200, 'edit moves expense to another month');
  assert.equal((await request(a, '/api?month=2026-10')).data.expenses, 99.99); checks++;
  for (const [m, amount] of [[month, '500'], ['2026-10', '900'], [month, '0']]) status(await request(a, `/api/budget?month=${m}`, 'PUT', { monthlyBudget: amount }), 200, 'month budget save/clear');
  assert.equal((await request(a, `/api?month=${month}`)).data.profile.monthlyBudget, 0); checks++;
  assert.equal((await request(a, '/api?month=2026-10')).data.profile.monthlyBudget, 900); checks++;
  status(await request(a, '/api?month=0999-01'), 400, 'out-of-range month rejected');
  status(await request(a, '/auth/logout', 'POST'), 200, 'logout');
  status(await request(a, '/api'), 401, 'logged-out session rejected');
  status(await request(a, '/auth/login', 'POST', { email: a.email.toUpperCase(), password }), 200, 'case-insensitive login');
  assert.equal((await request(a, '/api?month=2026-10')).data.expenses, 99.99); checks++;
  status(await request(a, `/api/transactions/${id}?month=2026-10`, 'DELETE'), 200, 'delete persists');
  assert.equal((await request(a, '/api?month=2026-10')).data.expenses, 0); checks++;
  console.log(`PASS ${checks} assertions. Test accounts: ${users.map(u => u.email).join(', ')}`);
} finally {
  for (const user of users) {
    if (!user.cookie) continue;
    for (const m of [month, '2026-10']) {
      const result = await request(user, `/api?month=${m}`);
      for (const item of result.data.transactions || []) await request(user, `/api/transactions/${item.id}?month=${m}`, 'DELETE');
      if (result.status === 200) await request(user, `/api/budget?month=${m}`, 'PUT', { monthlyBudget: 0 });
    }
    await request(user, '/auth/logout', 'POST');
  }
}
