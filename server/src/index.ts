import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import express, { type NextFunction, type Request, type Response } from 'express';
import pg from 'pg';
import { cross, latestRates, ratesOn, today } from './rates.ts';

pg.types.setTypeParser(1700, parseFloat);
pg.types.setTypeParser(1082, (value: string) => value);

if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is not set');

export const db = new pg.Pool({ connectionString: process.env.DATABASE_URL });
await db.query(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));

const ACCESS_TOKEN_TTL = 15 * 60;
const REFRESH_TOKEN_TTL = 30 * 24 * 60 * 60;

const httpError = (status: number, code: string, message: string) => Object.assign(new Error(message), { status, code });

const scryptHex = (password: string, salt: string) =>
  new Promise<string>((resolve, reject) =>
    scrypt(password, salt, 64, (err, key) => (err ? reject(err) : resolve(key.toString('hex')))),
  );

async function hashPassword(password: string) {
  const salt = randomBytes(16).toString('hex');
  return `${salt}:${await scryptHex(password, salt)}`;
}

async function checkPassword(password: string, stored: string) {
  const [salt, hash] = stored.split(':');
  return timingSafeEqual(Buffer.from(hash, 'hex'), Buffer.from(await scryptHex(password, salt), 'hex'));
}

function credentials(body: any) {
  const email = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
  const password = body?.password;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || typeof password !== 'string' || password.length < 8) return null;
  return { email, password };
}

type User = { id: number; email: string; display_name: string | null };

async function newSession(user: User) {
  const accessToken = randomBytes(32).toString('hex');
  const refreshToken = randomBytes(32).toString('hex');
  await db.query(
    `INSERT INTO sessions (user_id, access_token, refresh_token, access_expires_at, refresh_expires_at)
     VALUES ($1, $2, $3, now() + make_interval(secs => $4), now() + make_interval(secs => $5))`,
    [user.id, accessToken, refreshToken, ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL],
  );
  return {
    accessToken,
    refreshToken,
    tokenType: 'Bearer',
    expiresIn: ACCESS_TOKEN_TTL,
    user: { id: String(user.id), email: user.email, displayName: user.display_name },
  };
}

export const app = express();
app.use(express.json());

app.post('/auth/register', async (req, res) => {
  const creds = credentials(req.body);
  if (!creds) throw httpError(400, 'VALIDATION_ERROR', 'Enter a valid email and a password of at least 8 characters.');
  const displayName = typeof req.body.displayName === 'string' ? req.body.displayName.trim() || null : null;
  const { rows } = await db.query(
    `INSERT INTO users (email, password_hash, display_name) VALUES ($1, $2, $3)
     ON CONFLICT (email) DO NOTHING RETURNING id, email, display_name`,
    [creds.email, await hashPassword(creds.password), displayName],
  );
  if (!rows[0]) throw httpError(409, 'EMAIL_ALREADY_IN_USE', 'An account already exists with this email.');
  res.status(201).json(await newSession(rows[0]));
});

app.post('/auth/login', async (req, res) => {
  const creds = credentials(req.body);
  const { rows } = await db.query('SELECT id, email, display_name, password_hash FROM users WHERE email = $1', [
    creds?.email,
  ]);
  if (!creds || !rows[0] || !(await checkPassword(creds.password, rows[0].password_hash))) {
    throw httpError(401, 'INVALID_CREDENTIALS', 'Wrong email or password.');
  }
  res.json(await newSession(rows[0]));
});

app.post('/auth/refresh', async (req, res) => {
  const { rows } = await db.query(
    `DELETE FROM sessions s USING users u
     WHERE s.refresh_token = $1 AND s.refresh_expires_at > now() AND u.id = s.user_id
     RETURNING u.id, u.email, u.display_name`,
    [req.body?.refreshToken],
  );
  if (!rows[0]) throw httpError(401, 'INVALID_REFRESH_TOKEN', 'Your session has expired, please log in again.');
  res.json(await newSession(rows[0]));
});

app.post('/auth/logout', async (req, res) => {
  await db.query('DELETE FROM sessions WHERE refresh_token = $1', [req.body?.refreshToken]);
  res.sendStatus(204);
});

app.use(async (req, res, next) => {
  const token = req.get('authorization')?.replace(/^Bearer /, '');
  const { rows } = await db.query(
    'SELECT user_id FROM sessions WHERE access_token = $1 AND access_expires_at > now()',
    [token],
  );
  if (!rows[0]) throw httpError(401, 'UNAUTHORIZED', 'Missing, invalid or expired access token.');
  res.locals.userId = rows[0].user_id;
  next();
});

const camelCase = (column: string) => column.replace(/_(\w)/g, (_, letter: string) => letter.toUpperCase());

async function transaction<T>(work: (client: pg.PoolClient) => Promise<T>) {
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

async function fillMissingRates(userId: number) {
  const { rows } = await db.query(
    `SELECT e.id, e.currency, e.date, u.home_currency AS home FROM expenses e JOIN users u ON u.id = e.user_id
     WHERE e.user_id = $1 AND e.rate IS NULL`,
    [userId],
  );
  if (!rows.length) return;
  const home: string = rows[0].home;
  const foreign = rows.filter((r) => r.currency !== home);
  const tables = foreign.length ? await ratesOn(foreign.map((r) => r.date)).catch(() => new Map()) : new Map();
  const found = rows
    .map((r) => ({ id: r.id, rate: tables.has(r.date) ? cross(tables.get(r.date), r.currency, home) : r.currency === home ? 1 : null }))
    .filter((r) => r.rate !== null);
  if (!found.length) return;
  await db.query(
    `UPDATE expenses e SET rate = v.rate FROM unnest($1::int[], $2::float8[]) AS v(id, rate)
     WHERE e.id = v.id AND e.user_id = $3 AND e.rate IS NULL AND (SELECT home_currency FROM users WHERE id = $3) = $4`,
    [found.map((r) => r.id), found.map((r) => r.rate), userId, home],
  );
}

const MAX_CHARGES_PER_CALL = 400;

export function addPeriod(date: string, period: string, times: number) {
  const [y, m, d] = date.split('-').map(Number);
  const iso = (value: Date) => value.toISOString().slice(0, 10);
  if (period === 'weekly') return iso(new Date(Date.UTC(y, m - 1, d + 7 * times)));
  const month = m - 1 + (period === 'yearly' ? 12 : 1) * times;
  return iso(new Date(Date.UTC(y, month, Math.min(d, new Date(Date.UTC(y, month + 1, 0)).getUTCDate()))));
}

async function chargeDueSubscriptions(userId: number) {
  const now = today();
  await transaction(async (client) => {
    const { rows } = await client.query(
      `SELECT id, name, amount, currency, category, period, next_date FROM subscriptions
       WHERE user_id = $1 AND next_date <= $2 FOR UPDATE`,
      [userId, now],
    );
    for (const sub of rows) {
      const dates: string[] = [];
      let next: string = sub.next_date;
      while (next <= now && dates.length < MAX_CHARGES_PER_CALL) {
        dates.push(next);
        next = addPeriod(sub.next_date, sub.period, dates.length);
      }
      await client.query(
        `INSERT INTO expenses (user_id, subscription_id, title, amount, currency, category, date)
         SELECT $1, $2, $3, $4, $5, $6, unnest($7::date[])`,
        [userId, sub.id, sub.name, sub.amount, sub.currency, sub.category ?? 'Subscriptions', dates],
      );
      await client.query('UPDATE subscriptions SET next_date = $1 WHERE id = $2', [next, sub.id]);
    }
  });
}

type CrudOptions = {
  readOnly?: string[];
  beforeList?: (userId: number) => Promise<unknown>;
  afterWrite?: (userId: number, id: number) => Promise<unknown>;
};

function crud(table: string, columns: string[], { readOnly = [], beforeList, afterWrite }: CrudOptions = {}) {
  const router = express.Router();
  const output = ['id', ...columns, ...readOnly].map((c) => `${c} AS "${camelCase(c)}"`).join(', ');
  const fields = (body: any) => columns.filter((c) => body?.[camelCase(c)] !== undefined);
  const written = async (userId: number, id: number) => {
    await afterWrite?.(userId, id);
    const { rows } = await db.query(`SELECT ${output} FROM ${table} WHERE id = $1`, [id]);
    return rows[0];
  };

  router.get('/', async (_req, res) => {
    await beforeList?.(res.locals.userId);
    const { rows } = await db.query(`SELECT ${output} FROM ${table} WHERE user_id = $1 ORDER BY id`, [
      res.locals.userId,
    ]);
    res.json(rows);
  });

  router.post('/', async (req, res) => {
    const cols = ['user_id', ...fields(req.body)];
    const values = [res.locals.userId, ...cols.slice(1).map((c) => req.body[camelCase(c)])];
    const { rows } = await db.query(
      `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${cols.map((_, i) => `$${i + 1}`).join(', ')}) RETURNING id`,
      values,
    );
    res.status(201).json(await written(res.locals.userId, rows[0].id));
  });

  router.put('/:id', async (req, res) => {
    const cols = fields(req.body);
    if (!cols.length) {
      throw httpError(400, 'VALIDATION_ERROR', `Nothing to update, fields are: ${columns.map(camelCase).join(', ')}.`);
    }
    const { rows } = await db.query(
      `UPDATE ${table} SET ${cols.map((c, i) => `${c} = $${i + 3}`).join(', ')} WHERE id = $1 AND user_id = $2 RETURNING id`,
      [req.params.id, res.locals.userId, ...cols.map((c) => req.body[camelCase(c)])],
    );
    if (!rows[0]) throw httpError(404, 'NOT_FOUND', 'Not found.');
    res.json(await written(res.locals.userId, rows[0].id));
  });

  router.delete('/:id', async (req, res) => {
    const { rowCount } = await db.query(`DELETE FROM ${table} WHERE id = $1 AND user_id = $2`, [
      req.params.id,
      res.locals.userId,
    ]);
    if (!rowCount) throw httpError(404, 'NOT_FOUND', 'Not found.');
    res.sendStatus(204);
  });

  return router;
}

app.use(
  '/expenses',
  crud('expenses', ['title', 'amount', 'currency', 'category', 'tags', 'date'], {
    readOnly: ['rate', 'subscription_id'],
    beforeList: async (userId) => {
      await chargeDueSubscriptions(userId);
      await fillMissingRates(userId);
    },
    afterWrite: async (userId, id) => {
      await db.query('UPDATE expenses SET rate = NULL WHERE id = $1', [id]);
      await fillMissingRates(userId);
    },
  }),
);
app.use(
  '/subscriptions',
  crud('subscriptions', ['name', 'amount', 'currency', 'category', 'period', 'next_date'], {
    beforeList: chargeDueSubscriptions,
    afterWrite: chargeDueSubscriptions,
  }),
);
app.use('/budgets', crud('budgets', ['category', 'amount', 'currency']));

const profile = (row: any) => ({
  id: String(row.id),
  email: row.email,
  displayName: row.display_name,
  homeCurrency: row.home_currency,
});

const homeCurrency = async (userId: number): Promise<string> =>
  (await db.query('SELECT home_currency FROM users WHERE id = $1', [userId])).rows[0].home_currency;

app.get('/me', async (_req, res) => {
  const { rows } = await db.query('SELECT id, email, display_name, home_currency FROM users WHERE id = $1', [
    res.locals.userId,
  ]);
  res.json(profile(rows[0]));
});

app.put('/me', async (req, res) => {
  const currency = req.body?.homeCurrency;
  if (typeof currency !== 'string' || !/^[A-Z]{3}$/.test(currency)) {
    throw httpError(400, 'VALIDATION_ERROR', 'Pick a valid currency.');
  }
  const known = await latestRates().then((latest) => latest.table, () => null);
  if (known && !(currency in known)) throw httpError(400, 'VALIDATION_ERROR', 'This currency is not supported.');
  const user = await transaction(async (client) => {
    const { rows } = await client.query(
      `UPDATE users u SET home_currency = $2 FROM users old WHERE u.id = $1 AND old.id = u.id
       RETURNING u.id, u.email, u.display_name, u.home_currency, old.home_currency AS previous`,
      [res.locals.userId, currency],
    );
    if (rows[0].previous !== currency) {
      await client.query('UPDATE expenses SET rate = NULL WHERE user_id = $1', [res.locals.userId]);
    }
    return rows[0];
  });
  await fillMissingRates(res.locals.userId);
  res.json(profile(user));
});

app.get('/rates', async (_req, res) => {
  const home = await homeCurrency(res.locals.userId);
  const latest = await latestRates().catch(() => {
    throw httpError(503, 'RATES_UNAVAILABLE', 'Exchange rates are unavailable right now.');
  });
  const rates = Object.fromEntries(
    Object.keys(latest.table)
      .map((currency) => [currency, cross(latest.table, currency, home)])
      .filter(([, rate]) => rate !== null),
  );
  res.json({ base: home, date: latest.date, rates });
});

const BUILT_IN_CATEGORIES = [
  'groceries',
  'restaurant',
  'transport',
  'subscriptions',
  'shopping',
  'health',
  'leisure',
  'travel',
  'other',
];
const CATEGORY_OUTPUT = 'id, name, color, icon';

function categoryFields(body: any) {
  const name = typeof body?.name === 'string' ? body.name.trim() : '';
  if (!name || name.length > 30) throw httpError(400, 'VALIDATION_ERROR', 'Give the category a name of 1 to 30 characters.');
  if (BUILT_IN_CATEGORIES.includes(name.toLowerCase())) {
    throw httpError(400, 'VALIDATION_ERROR', 'This category already exists.');
  }
  const key = (value: unknown) => (typeof value === 'string' && /^[a-z_]{1,20}$/.test(value) ? value : null);
  return { name, color: key(body.color), icon: key(body.icon) };
}

app.get('/categories', async (_req, res) => {
  const { rows } = await db.query(`SELECT ${CATEGORY_OUTPUT} FROM categories WHERE user_id = $1 ORDER BY id`, [
    res.locals.userId,
  ]);
  res.json(rows);
});

app.post('/categories', async (req, res) => {
  const { name, color, icon } = categoryFields(req.body);
  const { rows } = await db.query(
    `INSERT INTO categories (user_id, name, color, icon) VALUES ($1, $2, COALESCE($3, 'slate'), COALESCE($4, 'tag'))
     RETURNING ${CATEGORY_OUTPUT}`,
    [res.locals.userId, name, color, icon],
  );
  res.status(201).json(rows[0]);
});

app.put('/categories/:id', async (req, res) => {
  const { name, color, icon } = categoryFields(req.body);
  const userId = res.locals.userId;
  const category = await transaction(async (client) => {
    const old = await client.query('SELECT name FROM categories WHERE id = $1 AND user_id = $2 FOR UPDATE', [
      req.params.id,
      userId,
    ]);
    if (!old.rows[0]) throw httpError(404, 'NOT_FOUND', 'Not found.');
    const { rows } = await client.query(
      `UPDATE categories SET name = $2, color = COALESCE($3, color), icon = COALESCE($4, icon) WHERE id = $1
       RETURNING ${CATEGORY_OUTPUT}`,
      [req.params.id, name, color, icon],
    );
    for (const table of ['expenses', 'subscriptions', 'budgets']) {
      await client.query(`UPDATE ${table} SET category = $3 WHERE user_id = $1 AND category = $2`, [
        userId,
        old.rows[0].name,
        name,
      ]);
    }
    return rows[0];
  });
  res.json(category);
});

app.delete('/categories/:id', async (req, res) => {
  const userId = res.locals.userId;
  await transaction(async (client) => {
    const { rows } = await client.query('DELETE FROM categories WHERE id = $1 AND user_id = $2 RETURNING name', [
      req.params.id,
      userId,
    ]);
    if (!rows[0]) throw httpError(404, 'NOT_FOUND', 'Not found.');
    for (const table of ['expenses', 'subscriptions']) {
      await client.query(`UPDATE ${table} SET category = NULL WHERE user_id = $1 AND category = $2`, [userId, rows[0].name]);
    }
    await client.query('DELETE FROM budgets WHERE user_id = $1 AND category = $2', [userId, rows[0].name]);
  });
  res.sendStatus(204);
});

app.use((err: any, _req: Request, res: Response, _next: NextFunction) => {
  if (err instanceof pg.DatabaseError && err.code === '23505') {
    err = httpError(400, 'VALIDATION_ERROR', 'This name is already used.');
  }
  if (err instanceof pg.DatabaseError && /^2[23]/.test(err.code ?? '')) {
    const message = err.code === '22003' ? 'This amount is too large.' : 'Some fields are missing or invalid.';
    err = httpError(400, 'VALIDATION_ERROR', message);
  }
  const status = err.status ?? 500;
  if (status >= 500) {
    console.error(err);
    return res.status(status).json({ code: 'INTERNAL_ERROR', message: 'Something went wrong, please try again.' });
  }
  res.status(status).json({ code: typeof err.code === 'string' ? err.code : 'BAD_REQUEST', message: err.message });
});

if (import.meta.main) {
  const port = Number(process.env.PORT ?? 8080);
  app.listen(port, () => console.log(`MyBudget server listening on http://localhost:${port}`));
}
