# Finance API

Routes used by the app once signed in. They all need `Authorization: Bearer <accessToken>` and answer `401` without
a valid one (see [auth.md](auth.md) for the auth routes, the token refresh and the error format).
Bodies are JSON, fields in `camelCase`, dates are `YYYY-MM-DD` strings, amounts are JSON numbers.

## Running it locally

```bash
cd server
docker compose up -d   # PostgreSQL
npm install
npm start              # http://localhost:8080, the emulator reaches it at http://10.0.2.2:8080
npm test               # needs the database too; exchange rates are stubbed, no network needed
```

`DATABASE_URL` comes from `server/.env` (see `.env.example`). The schema is applied at startup (`schema.sql`).

## Expenses, subscriptions, budgets

Same CRUD shape for the three resources, each user only ever sees their own rows:

| Method | Route | Success |
|---|---|---|
| `GET` | `/expenses` | `200` + array |
| `POST` | `/expenses` | `201` + the created row |
| `PUT` | `/expenses/:id` | `200` + the updated row (only the fields present in the body are changed, `null` clears a field) |
| `DELETE` | `/expenses/:id` | `204` |

| Resource | Fields you send | Fields the server adds |
|---|---|---|
| `expenses` | `title`, `amount` (> 0), `currency` (ISO code, default `EUR`), `category`, `tags` (array), `date` (default today) | `id`, `rate`, `subscriptionId` |
| `subscriptions` | `name`, `amount`, `currency`, `category`, `period` (`weekly` / `monthly` / `yearly`), `nextDate` | `id` |
| `budgets` | `category` (`null` = global monthly budget), `amount`, `currency` | `id` |

Invalid data answers `400` with a user-readable `message`; an unknown id answers `404`.

### Exchange rate saved on each expense

`rate` is the number of home-currency units for 1 unit of the expense's currency, **on the date of the expense**
(ECB reference rates from [Frankfurter](https://frankfurter.dev); weekends and holidays use the previous business day,
today and future dates use the latest rates). The app shows `amount × rate` as the home-currency value, so a past
expense does not change value when rates move.

- It is computed by the server on every create/update (the client never sends it).
- `rate` is `null` while the rate API is unreachable; the server fills it on a later `GET /expenses`.
  The app then falls back to today's rates for that expense.

### Subscriptions are charged automatically

On `GET /expenses`, `GET /subscriptions` and when a subscription is saved, every subscription whose `nextDate` is today
or earlier is charged: one expense per due date (same name, amount, currency, category, `subscriptionId` set), and
`nextDate` moves to the next date after today. Monthly and yearly periods keep the day of the month (Jan 31 → Feb 28
→ Mar 31). Deleting a subscription keeps its past charges (their `subscriptionId` becomes `null`).

## Custom categories

The app has 9 built-in categories (Groceries, Restaurant, Transport, Subscriptions, Shopping, Health, Leisure,
Travel, Other). Users can add their own; rows still store the category **name** in their `category` field.

| Method | Route | Body | Success |
|---|---|---|---|
| `GET` | `/categories` | | `200` + `[{ "id", "name", "color", "icon" }]` |
| `POST` | `/categories` | `{ "name", "color"?, "icon"? }` | `201` + the category |
| `PUT` | `/categories/:id` | same | `200` + the category. A new name is applied to every expense, subscription and budget using the old one. |
| `DELETE` | `/categories/:id` | | `204`. Its expenses and subscriptions become uncategorised (`null`), its budget is deleted. |

`name`: 1 to 30 characters, unique per user (case-insensitive), not a built-in name (`400` otherwise, with a
readable message). `color` and `icon` are palette keys defined by the app (`ui/components/Categories.kt`, e.g.
`"amber"`, `"pets"`); the server only checks their format and defaults to `"slate"` / `"tag"`.

## Home currency and rates

| Method | Route | Body | Success |
|---|---|---|---|
| `GET` | `/me` | | `200` + `{ "id", "email", "displayName", "homeCurrency" }` |
| `PUT` | `/me` | `{ "homeCurrency": "KRW" }` | `200` + same as `GET /me`. Every expense's `rate` is recomputed for the new currency, at the rate of its own date. `400` for an unknown currency. |
| `GET` | `/rates` | | `200` + `{ "base": "EUR", "date": "2026-11-26", "rates": { "KRW": 0.000677, "EUR": 1, … } }` |

`/rates` gives today's rates as home-currency units (`base`) for 1 unit of each currency. The app uses them for budgets
and subscriptions, which are future spending. It answers `503` when the rate API is unreachable; the app then keeps
the last rates it saw.

## Offline (app side)

The app keeps the last data loaded from the server (lists, custom categories, home currency, rates) in a file per user in
`noBackupFilesDir`, shows it on the next start before the server answers, and deletes it on sign-out or when the
session expires. Offline the app is read-only: saving needs the server.
