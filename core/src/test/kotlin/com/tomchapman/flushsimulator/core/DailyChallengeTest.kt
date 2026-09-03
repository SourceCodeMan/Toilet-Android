package com.tomchapman.flushsimulator.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The daily has to be the same daily on an iPhone, or it is not a daily.
 *
 * The expected values here were produced by a Python transcription of
 * `DailyChallenge.swift`'s generator, run on `UInt64` arithmetic the way Swift does.
 * If the Kotlin ever drifts by a bit — a signed shift, a signed modulo — the first
 * number below goes wrong.
 */
class DailyChallengeTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `the generator is the iOS generator, bit for bit`() {
        val rng = SplitMix(9_376)
        assertEquals(3_332_427_778_502_107_871L, rng.next())
        assertEquals(4_187_295_113_894_824_702L, rng.next())
        assertEquals(7_274_853_898_817_714_693L, rng.next())
    }

    @Test
    fun `a given day is the same puzzle on both platforms`() {
        // 2026-09-03: Apple day 9376. What the iPhone next to you is playing.
        val today = DailyChallenge.forStamp(9_376)
        assertEquals("outhouse", today.fixtureId)
        assertEquals(0.02, today.startingGrime, 1e-12)
        assertEquals(4, today.paperTarget)
        assertEquals(76, today.number)

        val tomorrow = DailyChallenge.forStamp(9_377)
        assertEquals("victorian", tomorrow.fixtureId)
        assertEquals(0.43, tomorrow.startingGrime, 1e-12)
        assertEquals(5, tomorrow.paperTarget)

        // Day one, and a day well into next year.
        assertEquals(1, DailyChallenge.forStamp(9_301).number)
        assertEquals("orbital", DailyChallenge.forStamp(9_301).fixtureId)
        assertEquals(3, DailyChallenge.forStamp(9_301).paperTarget)
        assertEquals("orbital", DailyChallenge.forStamp(9_496).fixtureId)
        assertEquals(0.12, DailyChallenge.forStamp(9_496).startingGrime, 1e-12)
        assertEquals(2, DailyChallenge.forStamp(9_496).paperTarget)
    }

    @Test
    fun `today is counted from Apple's epoch, not Unix's`() {
        // 2026-09-03T12:00Z is Unix day 20699 and Apple day 9376.
        val noon = 1_788_436_800_000L
        assertEquals(20_699, Standings.stamp(noon, utc))
        assertEquals(9_376, DailyChallenge.today(noon, utc).stamp)
    }

    @Test
    fun `every day is playable`() {
        for (stamp in 9_300..9_700) {
            val c = DailyChallenge.forStamp(stamp)
            assertTrue(c.fixture in Fixture.all, "day $stamp picked nothing")
            assertTrue(c.startingGrime in 0.0..0.49, "day $stamp grime ${c.startingGrime}")
            assertTrue(c.paperTarget in 1..5, "day $stamp target ${c.paperTarget}")
        }
    }

    @Test
    fun `a result round-trips through settings and only for its own day`() {
        val settings = MapSettings()
        val run = DailyResult(
            stamp = 9_376,
            marks = listOf(DailyMark.Perfect, DailyMark.Golden, DailyMark.Clogged),
            score = 1_240,
        )
        run.save(settings)

        assertEquals(run, DailyResult.load(settings, todayStamp = 9_376))
        assertNull(DailyResult.load(settings, todayStamp = 9_377), "yesterday's attempt is not today's")
    }

    @Test
    fun `a corrupted result starts fresh rather than throwing`() {
        for (junk in listOf("", "nonsense", "9376", "9376,abc,", "9376,10,Perfect;NotAMark")) {
            val settings = MapSettings(mapOf("dailyResult" to junk))
            assertNull(DailyResult.load(settings, 9_376), "junk: $junk")
        }
    }

    @Test
    fun `an empty grid is still a valid, unfinished day`() {
        val settings = MapSettings()
        DailyResult(9_376).save(settings)
        val loaded = DailyResult.load(settings, 9_376)
        assertEquals(DailyResult(9_376), loaded)
        assertTrue(loaded?.isComplete == false)
    }

    @Test
    fun `the share text is the grid and nothing else`() {
        val run = DailyResult(
            stamp = 9_376,
            marks = listOf(DailyMark.Golden, DailyMark.Perfect, DailyMark.Good, DailyMark.Poor, DailyMark.Clogged),
            score = 1_240,
        )
        val text = run.shareText(DailyChallenge.forStamp(9_376))
        val lines = text.lines()
        assertEquals("Flush Simulator — Daily #76", lines[0])
        assertEquals("🟨🟩🟦⬜🟥", lines[1])
        assertEquals("1,240 points · The Outhouse", lines[2])
        assertTrue(!text.contains("http"), "no link, no tracking")
    }
}
