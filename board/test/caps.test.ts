import assert from 'node:assert/strict'
import { test } from 'node:test'
import { cap, cleanName } from '../src/index.ts'

// The caps are the only thing between the board and anyone with `curl`, so they get
// tested against the attacks they exist to stop rather than against the happy path.

const HOUR = 3_600
const NOW = 1_788_264_000

const seen = (lifetime: number, best_day: number, updated_at: number) => ({
  lifetime,
  best_day,
  updated_at,
})

test('a first submission is held to the opening ceiling', () => {
  assert.equal(cap(999_999_999, 0, null, NOW).lifetime, 50_000)
})

test('a first submission below the ceiling is taken as given', () => {
  assert.equal(cap(1, 100, null, NOW).lifetime, 1)
  assert.equal(cap(4_200, 100, null, NOW).lifetime, 4_200)
})

test('growth is held to what the elapsed time could have produced', () => {
  // An hour allows 3600 / 1.872 = 1923 flushes, plus the grace.
  const previous = seen(1_000, 0, NOW - HOUR)
  assert.equal(cap(999_999, 0, previous, NOW).lifetime, 1_000 + 1_923 + 10)
})

test('growth inside the allowance passes through untouched', () => {
  const previous = seen(1_000, 0, NOW - HOUR)
  assert.equal(cap(1_500, 0, previous, NOW).lifetime, 1_500)
})

test('a week offline earns a week of flushes, because that is what is possible', () => {
  const previous = seen(0, 0, NOW - 7 * 24 * HOUR)
  const allowed = cap(999_999_999, 0, previous, NOW).lifetime
  assert.equal(allowed, Math.floor((7 * 24 * HOUR) / 1.872) + 10)
})

test('a tally never goes backwards', () => {
  const previous = seen(5_000, 700, NOW - HOUR)
  assert.equal(cap(3, 0, previous, NOW).lifetime, 5_000)
})

test('a repeat submission at the same second still allows the grace', () => {
  const previous = seen(100, 0, NOW)
  assert.equal(cap(999, 0, previous, NOW).lifetime, 110)
})

test('a clock that runs backwards does not widen the allowance', () => {
  const previous = seen(100, 0, NOW + 10 * HOUR)
  assert.equal(cap(999_999, 0, previous, NOW).lifetime, 110)
})

test('best day cannot exceed what the tally could be worth', () => {
  // 690 is the most one flush can be worth: five squares, golden.
  assert.equal(cap(10, 999_999_999, null, NOW).bestDay, 6_900)
})

test('a plausible best day survives', () => {
  assert.equal(cap(1_000, 4_200, null, NOW).bestDay, 4_200)
})

test('a best day never goes backwards', () => {
  const previous = seen(1_000, 5_000, NOW - HOUR)
  assert.equal(cap(1_000, 12, previous, NOW).bestDay, 5_000)
})

test('the forged first submission is the attack this does not fully stop', () => {
  // Worth pinning so the limitation is visible rather than assumed away: a fresh
  // uuid can claim the opening ceiling outright.
  assert.equal(cap(50_000, 999_999_999, null, NOW).lifetime, 50_000)
})

test('names are trimmed, collapsed and bounded', () => {
  assert.equal(cleanName('  Tom   Chapman  '), 'Tom Chapman')
  assert.equal(cleanName('x'.repeat(100)), 'x'.repeat(24))
  // Control characters become a space rather than vanishing, so a pasted
  // newline leaves two words rather than welding them into one.
  assert.equal(cleanName('line\nbreak'), 'line break')
  assert.equal(cleanName('a\u0000bc'), 'a bc')
})

test('the length cap does not cut a character in half', () => {
  // Sliced by UTF-16 unit this would end on half a surrogate pair.
  const name = cleanName('\u{1F6BD}'.repeat(30))!
  assert.equal(Array.from(name).length, 24)
  // Array.from walks code points, so a half-pair would surface here as a single
  // unit inside the surrogate range. A well-formed pair never does.
  const lone = Array.from(name).filter((c) => {
    const cp = c.codePointAt(0)!
    return cp >= 0xd800 && cp <= 0xdfff
  })
  assert.deepEqual(lone, [])
})

test('a name that is not a name is refused', () => {
  assert.equal(cleanName(''), null)
  assert.equal(cleanName('   '), null)
  assert.equal(cleanName('\u0000\u0001'), null)
  assert.equal(cleanName(42), null)
  assert.equal(cleanName(null), null)
  assert.equal(cleanName(undefined), null)
})
