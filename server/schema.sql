CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name TEXT
);

CREATE TABLE IF NOT EXISTS sessions (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users ON DELETE CASCADE,
  access_token TEXT NOT NULL UNIQUE,
  refresh_token TEXT NOT NULL UNIQUE,
  access_expires_at TIMESTAMPTZ NOT NULL,
  refresh_expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS expenses (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users ON DELETE CASCADE,
  title TEXT NOT NULL,
  amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
  currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
  category TEXT,
  tags TEXT[] NOT NULL DEFAULT '{}',
  date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS subscriptions (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users ON DELETE CASCADE,
  name TEXT NOT NULL,
  amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
  currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
  category TEXT,
  period TEXT NOT NULL DEFAULT 'monthly' CHECK (period IN ('weekly', 'monthly', 'yearly')),
  next_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS budgets (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users ON DELETE CASCADE,
  category TEXT,
  amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
  currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$')
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS home_currency TEXT NOT NULL DEFAULT 'EUR' CHECK (home_currency ~ '^[A-Z]{3}$');
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS rate DOUBLE PRECISION;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS subscription_id INT REFERENCES subscriptions ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS categories (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users ON DELETE CASCADE,
  name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 30),
  color TEXT NOT NULL DEFAULT 'slate',
  icon TEXT NOT NULL DEFAULT 'tag'
);
CREATE UNIQUE INDEX IF NOT EXISTS categories_user_name ON categories (user_id, lower(name));
