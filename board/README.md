# toilet-board

The global leaderboard, as a Cloudflare Worker over D1. One board for both platforms.

This replaces Game Center, which the iOS app reached for and never actually got — the
leaderboards need a paid App Store Connect membership to configure, and the Xcode
project has no Game Center entitlement anyway. Game Center would also have been a
*separate* board from Android's.

## Deploying

Not deployed yet — these need your Cloudflare login.

```sh
cd board
npm install
npx wrangler login
npx wrangler d1 create toilet-board
```

Copy the `database_id` it prints into `wrangler.jsonc`, then:

```sh
npx wrangler d1 execute toilet-board --remote --file schema.sql
npx wrangler deploy
```

Locally, `npx wrangler d1 execute toilet-board --local --file schema.sql` once, then
`npx wrangler dev`.

```sh
npm test        # the plausibility caps
npm run typecheck
```

## The API

**`POST /v1/scores`** — `{ id, name, lifetime, bestDay }`

`id` is a UUID the client generates once and keeps. There is no account and no login,
so that id is the only thing standing between someone and your row; it is never
returned to anyone else. Responds with the figures actually accepted, plus your rank:
`{ lifetime, bestDay, rank }`. A clamped client sees what landed rather than what it
sent.

**`GET /v1/board?id=<yours>`** — the top twenty.

`{ entries: [{ rank, name, lifetime, bestDay, isYou }] }`. The `id` is optional and is
only used to set `isYou`.

## What the caps do

A leaderboard for a client-side game is forgeable by definition — anyone can `curl`
the endpoint. Rather than pretend otherwise, the server holds every submission to what
the game could physically have produced. Two of the three numbers come out of the
game's own rules:

- **`MIN_SECONDS_PER_FLUSH = 1.872`** — the Chrome fixture is the shortest flush at
  2.6s, a weak pull shortens one to 0.72 of its length, and the engine refuses to
  start another flush while one is running.
- **`MAX_POINTS_PER_FLUSH = 690`** — `Upkeep.points(paper = 5, golden = true)`.
- **`FIRST_SUBMISSION_MAX = 50_000`** — the judgement call. A player nobody has seen
  before has no earlier observation to measure growth against, so the opening claim is
  capped at roughly a day of theoretical nonstop flushing.

From those: a tally never moves backwards, growth between two submissions never
exceeds what the elapsed seconds could have produced, and a best day never exceeds
what the whole lifetime tally could be worth.

### What they don't stop

Stated plainly, because a security control you have not bounded is worse than none:

- **A forged first submission.** A fresh UUID can claim 50,000 flushes outright, and
  can do it repeatedly with new UUIDs. Pinned by a test so it stays visible.
- **A patient cheater.** The growth bound is what is *physically possible*, not what
  is humanly likely — someone who waits a month may then claim a month of nonstop
  flushing. Tightening this means modelling waking hours, which would start rejecting
  real players.
- **Row spam.** Nothing rate-limits row creation. If it becomes a problem, Cloudflare's
  rate-limiting binding is config rather than code.

Anyone holding your player id can also overwrite your row. That is the cost of having
no accounts, and it is the right trade for a toilet.
