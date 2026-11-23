import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import type { AddressInfo } from 'node:net';
import { after, test } from 'node:test';
import { app, db } from './index.ts';

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
    { id: 0, title: 'Ramen', amount: 9000, currency: 'KRW', category: null, tags: ['seoul'], date: '2026-11-20' },
  );
  const id = created.body.id;

  const subscription = await call('POST', '/subscriptions', token, { name: 'Netflix', amount: 13.99, nextDate: '2026-12-01' });
  assert.equal(subscription.status, 201);
  assert.equal(subscription.body.nextDate, '2026-12-01');
  assert.equal(subscription.body.period, 'monthly');

  const invalid = await call('POST', '/expenses', token, { title: 'Bad', amount: -5 });
  assert.deepEqual([invalid.status, invalid.body.code], [400, 'VALIDATION_ERROR']);
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
