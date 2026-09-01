/**
 * The global leaderboard.
 *
 * Replaces Game Center, which the iOS app reached for and never actually got — the
 * boards need a paid App Store Connect membership to configure, and would have been
 * a separate board from Android's anyway. One Worker serves both platforms.
 *
 * There is no account and no login. A player is a UUID the client generates once and
 * keeps; that id is the only thing standing between someone and your row, so it is
 * never returned to anybody else.
 */

export interface Env {
  DB: D1Database
}

/**
 * The quickest a flush can physically be: the Chrome fixture is the shortest at
 * 2.6s, and a weak pull shortens one to 0.72 of its length. The engine refuses to
 * start another flush while one is running, so this is a real floor on the cycle.
 */
const MIN_SECONDS_PER_FLUSH = 1.872

/** The most a single flush can be worth: `Upkeep.points(paper = 5, golden = true)`. */
const MAX_POINTS_PER_FLUSH = 690

/**
 * The one number below that is judgement rather than arithmetic.
 *
 * A player we have never seen arrives with whatever they racked up offline, and
 * there is no earlier observation to measure the growth against. This is about a
 * day of theoretical nonstop flushing — far past anything a person reaches, and
 * short enough that a fabricated first submission is not worth making.
 */
const FIRST_SUBMISSION_MAX = 50_000

/** Clock skew and submissions that arrive in a batch, in flushes. */
const GRACE = 10

const BOARD_SIZE = 20
const MAX_NAME_LENGTH = 24

type Stored = { lifetime: number; best_day: number; updated_at: number }

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)

    if (request.method === 'POST' && url.pathname === '/v1/scores') {
      return submit(request, env)
    }
    if (request.method === 'GET' && url.pathname === '/v1/board') {
      return board(url, env)
    }
    return json({ error: 'not found' }, 404)
  },
}

async function submit(request: Request, env: Env): Promise<Response> {
  let body: Record<string, unknown>
  try {
    body = (await request.json()) as Record<string, unknown>
  } catch {
    return json({ error: 'expected a json body' }, 400)
  }

  const id = body.id
  if (typeof id !== 'string' || !PLAYER_ID.test(id)) {
    return json({ error: 'id must be a uuid' }, 400)
  }

  const name = cleanName(body.name)
  if (!name) return json({ error: 'name must be 1 to 24 printable characters' }, 400)

  if (!isCount(body.lifetime) || !isCount(body.bestDay)) {
    return json({ error: 'lifetime and bestDay must be whole numbers, 0 or more' }, 400)
  }

  const now = Math.floor(Date.now() / 1000)
  const previous = await env.DB
    .prepare('SELECT lifetime, best_day, updated_at FROM players WHERE id = ?')
    .bind(id)
    .first<Stored>()

  const accepted = cap(body.lifetime, body.bestDay, previous, now)

  await env.DB
    .prepare(
      `INSERT INTO players (id, name, lifetime, best_day, updated_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         name = excluded.name,
         lifetime = excluded.lifetime,
         best_day = excluded.best_day,
         updated_at = excluded.updated_at`,
    )
    .bind(id, name, accepted.lifetime, accepted.bestDay, now)
    .run()

  const ahead = await env.DB
    .prepare('SELECT COUNT(*) AS n FROM players WHERE lifetime > ?')
    .bind(accepted.lifetime)
    .first<{ n: number }>()

  // The accepted figures, not the submitted ones: a client that has been clamped
  // should see what actually landed.
  return json({ ...accepted, rank: (ahead?.n ?? 0) + 1 })
}

async function board(url: URL, env: Env): Promise<Response> {
  // Only ever used to mark your own row. Ids are never sent back out.
  const me = url.searchParams.get('id')

  const { results } = await env.DB
    .prepare(
      `SELECT id, name, lifetime, best_day FROM players
       ORDER BY lifetime DESC, updated_at ASC LIMIT ?`,
    )
    .bind(BOARD_SIZE)
    .all<{ id: string; name: string; lifetime: number; best_day: number }>()

  return json({
    entries: results.map((row, i) => ({
      rank: i + 1,
      name: row.name,
      lifetime: row.lifetime,
      bestDay: row.best_day,
      isYou: row.id === me,
    })),
  })
}

/**
 * Hold a submission to what the game could actually have produced.
 *
 * Exported because these caps are the only thing between the board and anyone with
 * `curl`, so they are unit tested rather than trusted.
 */
export function cap(
  lifetime: number,
  bestDay: number,
  previous: Stored | null,
  now: number,
): { lifetime: number; bestDay: number } {
  let held: number

  if (!previous) {
    held = Math.min(lifetime, FIRST_SUBMISSION_MAX)
  } else {
    // What the elapsed time could have produced, flushed back to back. A player who
    // was offline for a week earned a week's worth, which is correct: the bound is
    // what is physically possible, not what is likely.
    const elapsed = Math.max(now - previous.updated_at, 0)
    const earned = Math.floor(elapsed / MIN_SECONDS_PER_FLUSH) + GRACE
    // A tally never goes backwards, so a stale or rolled-back submission keeps
    // whatever is already stored.
    held = Math.min(Math.max(lifetime, previous.lifetime), previous.lifetime + earned)
  }

  // You cannot have scored more on your best day than your whole tally is worth.
  const heldBestDay = Math.min(bestDay, held * MAX_POINTS_PER_FLUSH)

  return { lifetime: held, bestDay: Math.max(heldBestDay, previous?.best_day ?? 0) }
}

const PLAYER_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 2_147_483_647
}

/** Trims, collapses runs of whitespace, and neutralises control characters. */
export function cleanName(value: unknown): string | null {
  if (typeof value !== 'string') return null
  // Control characters become spaces rather than vanishing, so a pasted newline
  // leaves two words rather than welding them into one.
  const collapsed = value
    .replace(/[\u0000-\u001F\u007F]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  // Cut by code point, so the length cap cannot slice an emoji in half.
  const cleaned = Array.from(collapsed).slice(0, MAX_NAME_LENGTH).join('').trim()
  return cleaned.length > 0 ? cleaned : null
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}
