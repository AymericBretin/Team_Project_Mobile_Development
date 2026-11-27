type Table = Record<string, number>;

const LATEST_TTL = 60 * 60 * 1000;
const RETRY_AFTER_FAILURE = 60 * 1000;

export const rateSource = {
  async get(path: string): Promise<any> {
    const res = await fetch(`https://api.frankfurter.dev/v1/${path}`, { signal: AbortSignal.timeout(5000) });
    if (!res.ok) throw new Error(`Exchange-rate API answered ${res.status}`);
    return res.json();
  },
};

const byDay = new Map<string, Table>();
let latest: { table: Table; date: string; fetchedAt: number } | null = null;
let unavailableUntil = 0;

export function clearRateCache() {
  byDay.clear();
  latest = null;
  unavailableUntil = 0;
}

export function today() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

async function call(path: string) {
  if (Date.now() < unavailableUntil) throw new Error('Exchange-rate API unavailable');
  try {
    return await rateSource.get(path);
  } catch (err) {
    unavailableUntil = Date.now() + RETRY_AFTER_FAILURE;
    throw err;
  }
}

export async function latestRates() {
  if (!latest || Date.now() - latest.fetchedAt > LATEST_TTL) {
    const body = await call('latest?base=EUR');
    latest = { table: { ...body.rates, EUR: 1 }, date: body.date, fetchedAt: Date.now() };
  }
  return latest;
}

export async function ratesOn(dates: string[]) {
  const now = today();
  const result = new Map<string, Table>();
  const missing = [...new Set(dates)].filter((d) => d < now && !byDay.has(d)).sort();
  if (missing.length) {
    const body = await call(`${missing[0]}..${missing.at(-1)}?base=EUR`);
    const days = Object.keys(body.rates).sort();
    for (const date of missing) {
      const day = days.findLast((d) => d <= date);
      if (day) byDay.set(date, { ...body.rates[day], EUR: 1 });
    }
  }
  for (const date of new Set(dates)) {
    const table = date < now ? byDay.get(date) : (await latestRates()).table;
    if (table) result.set(date, table);
  }
  return result;
}

export function cross(table: Table, from: string, to: string) {
  if (from === to) return 1;
  return table[from] && table[to] ? table[to] / table[from] : null;
}
