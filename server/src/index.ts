import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import express, { type NextFunction, type Request, type Response } from 'express';
import pg from 'pg';

// Send NUMERIC (1700) as JSON numbers and DATE (1082) as 'YYYY-MM-DD' instead of timezone-shifted JS Dates.
pg.types.setTypeParser(1700, parseFloat);
pg.types.setTypeParser(1082, (value: string) => value);

export const db = new pg.Pool({
  connectionString: process.env.DATABASE_URL ?? 'postgres://mybudget:mybudget@localhost:5432/mybudget',
});
await db.query(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));

const ACCESS_TOKEN_TTL = 15 * 60; // seconds
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

// Same rules as the app's CredentialsValidator.
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
  // Deleting the session invalidates both old tokens; only one of two concurrent refreshes can win.
  const { rows } = await db.query(
    `DELETE FROM sessions s USING users u
     WHERE s.refresh_token = $1 AND s.refresh_expires_at > now() AND u.id = s.user_id
     RETURNING u.id, u.email, u.display_name`,
    [req.body?.refreshToken],
  );
  if (!rows[0]) throw httpError(401, 'INVALID_REFRESH_TOKEN', 'Your session has expired, please log in again.');
  res.json(await newSession(rows[0]));
});

// Needs no access token: the refresh token alone proves the session, and the app ignores failures.
app.post('/auth/logout', async (req, res) => {
  await db.query('DELETE FROM sessions WHERE refresh_token = $1', [req.body?.refreshToken]);
  res.sendStatus(204);
});

// Every route below requires `Authorization: Bearer <accessToken>`, and answers 401 so the app refreshes it.
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

// CRUD on a table whose rows belong to the logged-in user. JSON fields are the camelCase column names.
// Only the whitelisted column names reach the SQL text; values always go through parameters,
// and the table constraints in schema.sql do the validation.
function crud(table: string, columns: string[]) {
  const router = express.Router();
  const output = ['id', ...columns].map((c) => `${c} AS "${camelCase(c)}"`).join(', ');
  const fields = (body: any) => columns.filter((c) => body?.[camelCase(c)] !== undefined);

  router.get('/', async (_req, res) => {
    const { rows } = await db.query(`SELECT ${output} FROM ${table} WHERE user_id = $1 ORDER BY id`, [
      res.locals.userId,
    ]);
    res.json(rows);
  });

  router.post('/', async (req, res) => {
    const cols = ['user_id', ...fields(req.body)];
    const values = [res.locals.userId, ...cols.slice(1).map((c) => req.body[camelCase(c)])];
    const { rows } = await db.query(
      `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${cols.map((_, i) => `$${i + 1}`).join(', ')}) RETURNING ${output}`,
      values,
    );
    res.status(201).json(rows[0]);
  });

  router.put('/:id', async (req, res) => {
    const cols = fields(req.body);
    if (!cols.length) {
      throw httpError(400, 'VALIDATION_ERROR', `Nothing to update, fields are: ${columns.map(camelCase).join(', ')}.`);
    }
    const { rows } = await db.query(
      `UPDATE ${table} SET ${cols.map((c, i) => `${c} = $${i + 3}`).join(', ')} WHERE id = $1 AND user_id = $2 RETURNING ${output}`,
      [req.params.id, res.locals.userId, ...cols.map((c) => req.body[camelCase(c)])],
    );
    if (!rows[0]) throw httpError(404, 'NOT_FOUND', 'Not found.');
    res.json(rows[0]);
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

app.use('/expenses', crud('expenses', ['title', 'amount', 'currency', 'category', 'tags', 'date']));
app.use('/subscriptions', crud('subscriptions', ['name', 'amount', 'currency', 'category', 'period', 'next_date']));
app.use('/budgets', crud('budgets', ['category', 'amount', 'currency']));

// Errors follow the app's format: { code, message }.
app.use((err: any, _req: Request, res: Response, _next: NextFunction) => {
  // Postgres error classes 22 (bad value) and 23 (constraint violation) mean the client sent bad data.
  if (err instanceof pg.DatabaseError && /^2[23]/.test(err.code ?? '')) {
    err = httpError(400, 'VALIDATION_ERROR', err.message);
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
