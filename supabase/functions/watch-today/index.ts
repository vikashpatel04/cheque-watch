// watch-today: read-only summary of today's cheques for the Wear OS companion.
//
// GET only. Requires the `x-watch-key` header to match the WATCH_KEY secret.
// Uses the database connection Supabase provides to every Edge Function
// (SUPABASE_DB_URL). It never writes: the only SQL is the two fixed SELECTs
// below, run inside a READ ONLY transaction, which Postgres itself enforces.
//
// Mirrors the web app's dashboard (TodayPanel):
//   cheques        due_date = today in Asia/Kolkata, status PENDING or DEPOSITED,
//                  not soft-deleted, largest amount first
//   amount_needed  sum of the PENDING ones only (money that still has to be in
//                  the bank today; a DEPOSITED cheque is already covered), like
//                  the web app's "Cash needed today"
//   overdue_*      same filter with due_date < today: count of all, amount of
//                  the PENDING ones (no list)
//
// Secrets:
//   WATCH_KEY      long random string the watch sends as x-watch-key (required)
//   WATCH_USER_ID  auth.users id that owns the cheques (optional; the app has a
//                  single login, set it only if more users are ever added)
//   SUPABASE_DB_URL is provided automatically by Supabase.

import postgres from 'npm:postgres@3.4.7'

const TIME_ZONE = 'Asia/Kolkata'
const OPEN_STATUSES = ['PENDING', 'DEPOSITED']

const DB_URL = Deno.env.get('SUPABASE_DB_URL') ?? ''
const WATCH_KEY = Deno.env.get('WATCH_KEY') ?? ''
const USER_ID = Deno.env.get('WATCH_USER_ID') || null

// One connection is plenty: a single person's watch calls this about once an
// hour. prepare: false keeps it compatible with the transaction pooler.
const sql = DB_URL
  ? postgres(DB_URL, { prepare: false, max: 1, idle_timeout: 20, connect_timeout: 10 })
  : null

/** Today's date in IST as YYYY-MM-DD, whatever the server's clock zone. */
function todayInIst(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const get = (type: string) => parts.find((p) => p.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')}`
}

/** Constant-time comparison of the header against the secret. */
async function keyMatches(given: string | null): Promise<boolean> {
  if (!given || !WATCH_KEY) return false
  const enc = new TextEncoder()
  const [a, b] = await Promise.all([
    crypto.subtle.digest('SHA-256', enc.encode(given)),
    crypto.subtle.digest('SHA-256', enc.encode(WATCH_KEY)),
  ])
  const x = new Uint8Array(a)
  const y = new Uint8Array(b)
  let diff = 0
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i]
  return diff === 0
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  })
}

/** Sum in paise so 2-decimal amounts add up exactly. */
function sumAmounts(amounts: number[]): number {
  return amounts.reduce((s, a) => s + Math.round(a * 100), 0) / 100
}

Deno.serve(async (req) => {
  if (req.method !== 'GET') {
    return json(405, { error: 'Method not allowed' })
  }
  if (!(await keyMatches(req.headers.get('x-watch-key')))) {
    return json(401, { error: 'Unauthorized' })
  }
  if (!sql) {
    console.error('watch-today: SUPABASE_DB_URL is not set')
    return json(500, { error: 'Not configured' })
  }

  const now = new Date()
  const today = todayInIst(now)

  try {
    const { rows, overdue } = await sql.begin('read only', async (tx) => {
      const rows = await tx`
        select c.id,
               coalesce(p.name, 'Unknown')   as party_name,
               c.amount::float8               as amount,
               c.status,
               c.cheque_number,
               c.bank_name,
               c.due_date::text               as due_date,
               c.original_due_date::text      as original_due_date,
               c.represent_count,
               c.return_reason
        from public.cheques c
        left join public.parties p on p.id = c.party_id
        where (${USER_ID}::uuid is null or c.user_id = ${USER_ID}::uuid)
          and c.deleted_at is null
          and c.status in ${tx(OPEN_STATUSES)}
          and c.due_date = ${today}::date
        order by c.amount desc, party_name, c.id
      `
      const [overdue] = await tx`
        select count(*)::int as count,
               coalesce(sum(c.amount) filter (where c.status = 'PENDING'), 0)::float8 as amount_needed
        from public.cheques c
        where (${USER_ID}::uuid is null or c.user_id = ${USER_ID}::uuid)
          and c.deleted_at is null
          and c.status in ${tx(OPEN_STATUSES)}
          and c.due_date < ${today}::date
      `
      return { rows, overdue }
    })

    const cheques = rows.map((r) => ({
      id: r.id as string,
      party_name: r.party_name as string,
      amount: r.amount as number,
      status: r.status as string,
      cheque_number: r.cheque_number as string,
      bank_name: r.bank_name as string,
      due_date: r.due_date as string,
      original_due_date: (r.original_due_date as string | null) ?? null,
      represent_count: (r.represent_count as number | null) ?? 0,
      return_reason: (r.return_reason as string | null) ?? null,
    }))

    return json(200, {
      date: today,
      amount_needed: sumAmounts(cheques.filter((c) => c.status === 'PENDING').map((c) => c.amount)),
      count: cheques.length,
      overdue_count: overdue.count as number,
      overdue_amount_needed: Math.round((overdue.amount_needed as number) * 100) / 100,
      updated_at: now.toISOString(),
      cheques,
    })
  } catch (err) {
    console.error('watch-today: query failed', err)
    return json(500, { error: 'Query failed' })
  }
})
