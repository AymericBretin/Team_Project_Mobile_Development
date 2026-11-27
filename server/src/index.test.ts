import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import type { AddressInfo } from 'node:net';
import { after, beforeEach, test } from 'node:test';
import { addPeriod, app, db } from './index.ts';
import { clearRateCache, rateSource, today } from './rates.ts';

const rateCalls: string[] = [];
let ratesDown = false;
rateSource.get = async (path: string) => {
  rateCalls.push(path);
  if (ratesDown) throw new Error('offline');
  if (path.startsWith('latest')) return { date: '2026-11-26', rates: { KRW: 1500, USD: 1.1 } };
  return { rates: { '2026-11-20': { KRW: 1400, USD: 1 }, '2026-11-23': { KRW: 1450, USD: 1.05 } } };
};
beforeEach(() => {
  clearRateCache();
  rateCalls.length = 0;
  ratesDown = false;
});

const shiftDays = (days: number) => {
  const [y, m, d] = today().split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
};

const server = app.listen(0);
await once(server, 'listening');
const base = `http://localhost:${(server.address() as AddressInfo).port}`;
after(async () => {
  server.close();
  await db.end();
});

async function call(method: string, path: string, token?: string, body?: unknown) {
  const res = await fetch(base + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token && { authorization: `Bearer ${token}` }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: res.status, body: res.status === 204 ? null : await res.json() };
}

const register = (email = `${randomUUID()}@test.dev`) =>
  call('POST', '/auth/register', undefined, { email, password: 'password123', displayName: ' Jane ' });

test('register, login, refresh rotation and logout follow the app contract', async () => {
  const email = `${randomUUID()}@test.dev`;
  const registered = await register(email);
  assert.equal(registered.status, 201);
  assert.equal(registered.body.tokenType, 'Bearer');
  assert.equal(registered.body.expiresIn, 900);
  assert.deepEqual({ ...registered.body.user, id: typeof registered.body.user.id }, {
    id: 'string',
    email,
    displayName: 'Jane',
  });

  assert.deepEqual((await register(email)).body.code, 'EMAIL_ALREADY_IN_USE');
  assert.equal((await call('POST', '/auth/register', undefined, { email: 'a@b', password: 'password123' })).status, 400);
  const wrong = await call('POST', '/auth/login', undefined, { email, password: 'wrong-password' });
  assert.deepEqual([wrong.status, wrong.body.code], [401, 'INVALID_CREDENTIALS']);

  const login = await call('POST', '/auth/login', undefined, { email: ` ${email.toUpperCase()} `, password: 'password123' });
  assert.equal(login.status, 200);
  const { accessToken, refreshToken } = login.body;
  assert.equal((await call('GET', '/expenses', accessToken)).status, 200);
  assert.equal((await call('GET', '/expenses')).status, 401);

  await db.query('UPDATE sessions SET access_expires_at = now() WHERE access_token = $1', [accessToken]);
  assert.equal((await call('GET', '/expenses', accessToken)).status, 401);

  const refreshed = await call('POST', '/auth/refresh', undefined, { refreshToken });
  assert.equal(refreshed.status, 200);
  assert.equal(refreshed.body.user.email, email);
  assert.equal((await call('GET', '/expenses', refreshed.body.accessToken)).status, 200);
  assert.equal((await call('POST', '/auth/refresh', undefined, { refreshToken })).status, 401);

  assert.equal((await call('POST', '/auth/logout', undefined, { refreshToken: refreshed.body.refreshToken })).status, 204);
  assert.equal((await call('GET', '/expenses', refreshed.body.accessToken)).status, 401);
  assert.equal((await call('POST', '/auth/refresh', undefined, { refreshToken: refreshed.body.refreshToken })).status, 401);
  assert.equal((await call('GET', '/expenses', registered.body.accessToken)).status, 200);
});

test('CRUD uses camelCase fields, validates input and isolates users', async () => {
  const token = (await register()).body.accessToken;

  const created = await call('POST', '/expenses', token, {
    title: 'Ramen',
    amount: 9000,
    currency: 'KRW',
    tags: ['seoul'],
    date: '2026-11-20',
  });
  assert.equal(created.status, 201);
  assert.deepEqual(
    { ...created.body, id: 0 },
    {
      id: 0,
      title: 'Ramen',
      amount: 9000,
      currency: 'KRW',
      category: null,
      tags: ['seoul'],
      date: '2026-11-20',
      rate: 1 / 1400,
      subscriptionId: null,
    },
  );
  const id = created.body.id;

  const subscription = await call('POST', '/subscriptions', token, { name: 'Netflix', amount: 13.99, nextDate: shiftDays(10) });
  assert.equal(subscription.status, 201);
  assert.equal(subscription.body.nextDate, shiftDays(10));
  assert.equal(subscription.body.period, 'monthly');

  const invalid = await call('POST', '/expenses', token, { title: 'Bad', amount: -5 });
  assert.equal(invalid.status, 400);
  assert.deepEqual(invalid.body, { code: 'VALIDATION_ERROR', message: 'Some fields are missing or invalid.' });
  const huge = await call('POST', '/expenses', token, { title: 'Bad', amount: 1e13 });
  assert.deepEqual(huge.body, { code: 'VALIDATION_ERROR', message: 'This amount is too large.' });
  assert.equal((await call('POST', '/expenses', token, { amount: 5 })).status, 400);
  assert.equal((await call('POST', '/expenses', token, { title: 'Bad', amount: 5, currency: 'euro' })).status, 400);

  const other = (await register()).body.accessToken;
  assert.deepEqual((await call('GET', '/expenses', other)).body, []);
  assert.equal((await call('PUT', `/expenses/${id}`, other, { amount: 1 })).status, 404);
  assert.equal((await call('DELETE', `/expenses/${id}`, other)).status, 404);

  const updated = await call('PUT', `/expenses/${id}`, token, { amount: 12.5, category: 'Food' });
  assert.equal(updated.status, 200);
  assert.equal(updated.body.amount, 12.5);
  assert.equal(updated.body.category, 'Food');
  assert.equal((await call('GET', '/expenses', token)).body.length, 1);

  assert.equal((await call('DELETE', `/expenses/${id}`, token)).status, 204);
  assert.deepEqual((await call('GET', '/expenses', token)).body, []);
});

test('expenses keep the exchange rate of their date, in the home currency', async () => {
  const token = (await register()).body.accessToken;
  const create = (currency: string, date: string) => call('POST', '/expenses', token, { title: 'x', amount: 10, currency, date });

  assert.equal((await create('EUR', '2026-11-20')).body.rate, 1);
  assert.equal((await create('KRW', '2026-11-22')).body.rate, 1 / 1400, 'a Sunday uses the previous business day');
  assert.equal((await create('USD', shiftDays(0))).body.rate, 1 / 1.1, "today's expenses use the latest rates");

  const rates = await call('GET', '/rates', token);
  assert.deepEqual(rates.body, { base: 'EUR', date: '2026-11-26', rates: { KRW: 1 / 1500, USD: 1 / 1.1, EUR: 1 } });

  const me = await call('PUT', '/me', token, { homeCurrency: 'KRW' });
  assert.equal(me.body.homeCurrency, 'KRW');
  assert.deepEqual((await call('GET', '/me', token)).body.homeCurrency, 'KRW');
  const converted = (await call('GET', '/expenses', token)).body.map((e: any) => [e.currency, e.rate]);
  assert.deepEqual(converted, [['EUR', 1400], ['KRW', 1], ['USD', 1500 / 1.1]]);
  assert.equal((await call('GET', '/rates', token)).body.rates.EUR, 1500);

  assert.equal((await call('PUT', '/me', token, { homeCurrency: 'XYZ' })).status, 400);
  assert.equal((await call('PUT', '/me', token, { homeCurrency: 'eur' })).status, 400);
});

test('an expense saved while the rate API is down gets its rate later', async () => {
  const token = (await register()).body.accessToken;
  ratesDown = true;
  const created = await call('POST', '/expenses', token, { title: 'x', amount: 10, currency: 'USD', date: '2026-11-23' });
  assert.equal(created.status, 201);
  assert.equal(created.body.rate, null);
  assert.equal((await call('GET', '/rates', token)).status, 503);

  clearRateCache();
  ratesDown = false;
  assert.equal((await call('GET', '/expenses', token)).body[0].rate, 1 / 1.05);
});

test('due subscriptions are charged as expenses and move to their next date', async () => {
  const token = (await register()).body.accessToken;
  const created = await call('POST', '/subscriptions', token, {
    name: 'Gym',
    amount: 30,
    period: 'weekly',
    category: 'Health',
    nextDate: shiftDays(-14),
  });
  assert.equal(created.body.nextDate, shiftDays(7));

  const lists = await Promise.all([1, 2, 3].map(() => call('GET', '/expenses', token)));
  const charges = lists[2].body.map((e: any) => [e.title, e.amount, e.category, e.date, e.subscriptionId, e.rate]);
  assert.deepEqual(charges, [
    ['Gym', 30, 'Health', shiftDays(-14), created.body.id, 1],
    ['Gym', 30, 'Health', shiftDays(-7), created.body.id, 1],
    ['Gym', 30, 'Health', shiftDays(0), created.body.id, 1],
  ]);

  await call('DELETE', `/subscriptions/${created.body.id}`, token);
  const kept = (await call('GET', '/expenses', token)).body;
  assert.deepEqual(kept.map((e: any) => e.subscriptionId), [null, null, null]);
});

test('monthly and yearly periods keep the day of month', () => {
  assert.deepEqual([1, 2, 3].map((n) => addPeriod('2026-01-31', 'monthly', n)), ['2026-02-28', '2026-03-31', '2026-04-30']);
  assert.equal(addPeriod('2028-02-29', 'yearly', 1), '2029-02-28');
  assert.equal(addPeriod('2026-12-29', 'weekly', 1), '2027-01-05');
});

test('custom categories: renaming and deleting them follows through expenses, bills and budgets', async () => {
  const token = (await register()).body.accessToken;
  const pets = await call('POST', '/categories', token, { name: ' Pets ', color: 'amber', icon: 'pets' });
  assert.equal(pets.status, 201);
  assert.deepEqual({ ...pets.body, id: 0 }, { id: 0, name: 'Pets', color: 'amber', icon: 'pets' });

  assert.equal((await call('POST', '/categories', token, { name: 'pets' })).body.message, 'This name is already used.');
  assert.equal((await call('POST', '/categories', token, { name: 'Groceries' })).body.message, 'This category already exists.');
  assert.equal((await call('POST', '/categories', token, { name: '  ' })).status, 400);
  const defaults = (await call('POST', '/categories', token, { name: 'Gifts', color: 'Not a key!' })).body;
  assert.deepEqual([defaults.color, defaults.icon], ['slate', 'tag']);

  await call('POST', '/expenses', token, { title: 'Food', amount: 20, category: 'Pets', date: '2026-11-20' });
  await call('POST', '/subscriptions', token, { name: 'Vet plan', amount: 9, category: 'Pets', nextDate: shiftDays(5) });
  await call('POST', '/budgets', token, { category: 'Pets', amount: 50 });
  await call('POST', '/budgets', token, { category: null, amount: 1000 });

  const renamed = await call('PUT', `/categories/${pets.body.id}`, token, { name: 'Animals' });
  assert.deepEqual([renamed.body.name, renamed.body.color, renamed.body.icon], ['Animals', 'amber', 'pets']);
  const categoryOf = async (path: string) => (await call('GET', path, token)).body.map((row: any) => row.category);
  assert.deepEqual(await categoryOf('/expenses'), ['Animals']);
  assert.deepEqual(await categoryOf('/subscriptions'), ['Animals']);
  assert.deepEqual(await categoryOf('/budgets'), ['Animals', null]);

  const other = (await register()).body.accessToken;
  assert.equal((await call('PUT', `/categories/${pets.body.id}`, other, { name: 'Mine' })).status, 404);
  assert.equal((await call('DELETE', `/categories/${pets.body.id}`, other)).status, 404);

  assert.equal((await call('DELETE', `/categories/${pets.body.id}`, token)).status, 204);
  assert.deepEqual(await categoryOf('/expenses'), [null]);
  assert.deepEqual(await categoryOf('/subscriptions'), [null]);
  assert.deepEqual(await categoryOf('/budgets'), [null]);
  assert.deepEqual((await call('GET', '/categories', token)).body.map((c: any) => c.name), ['Gifts']);
});
