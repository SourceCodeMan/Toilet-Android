package com.tomchapman.flushsimulator.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine, driven end to end on virtual time.
 *
 * Everything the Swift reached for globally — storage, sound, buzz, the clock, the
 * dice — is handed in here, so a whole flush runs in microseconds and the parts that
 * used to be untestable (what the app says, when the streak dies, what a reset does
 * to a settle already in flight, what a tank is worth) are ordinary assertions.
 *
 * The clock is pinned to 2026-09-03, which makes today's daily the Outhouse with 2%
 * grime and a target of four squares — the same day an iPhone would deal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlushEngineTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    /** 2026-09-03T12:00Z. */
    private val noon = 1_788_436_800_000L

    private fun millis(profile: FlushProfile, grade: FlushGrade = FlushGrade.Good) =
        (grade.applyTo(profile).duration * 1_000).toLong()

    private val standardFlush = millis(FlushProfile.Standard)

    private fun TestScope.engine(
        settings: Settings = MapSettings(),
        random: Random = QueuedRandom(),
        audio: FlushAudio = RecordingAudio(),
        haptics: Haptics = RecordingHaptics(),
        clock: Clock = FakeClock(noon),
    ) = FlushEngine(
        settings = settings,
        scope = this,
        audio = audio,
        haptics = haptics,
        clock = clock,
        random = random,
        quips = Quips(Random(1)),
        zone = utc,
    )

    /**
     * Pulls the handle and lets the water settle — and no further. Running the clock
     * to idle would also run the message off and the gold out, and most of what
     * these tests read is exactly that.
     */
    private fun TestScope.flush(engine: FlushEngine, grade: FlushGrade = FlushGrade.Good) {
        engine.pullHandle(grade)
        advanceTimeBy(millis(engine.state.value.activeProfile) + 1)
    }

    /** Draws and tears a sheet, the way a finger would. */
    private fun load(engine: FlushEngine, squares: Int) {
        engine.pullPaper(squares)
        engine.cutPaper()
    }

    // Starting from nothing

    @Test
    fun `a fresh engine starts with a full tank and a bare roll`() = runTest {
        val state = engine().state.value
        assertEquals(0, state.totalFlushes)
        assertEquals(0, state.goldenFlushes)
        assertEquals(0, state.streak)
        assertEquals(0.0, state.grime)
        assertEquals(Upkeep.RUN_LENGTH, state.flushesLeft)
        assertEquals(0, state.runScore)
        assertEquals(0, state.paperPulled)
        assertTrue(!state.isPaperCut)
        assertEquals(Fixture.Standard, state.fixture)
        assertTrue(!state.isFlushing)
        assertTrue(!state.isClogged)
        assertNull(state.daily)
        assertNull(state.message)
        assertEquals(9_376, state.challenge.stamp)
    }

    // One flush

    @Test
    fun `nothing is tallied until the water settles`() = runTest {
        val engine = engine()
        engine.pullHandle(FlushGrade.Good)

        assertTrue(engine.state.value.isFlushing)
        assertEquals(0, engine.state.value.totalFlushes, "the flush is still running")

        advanceTimeBy(standardFlush - 1)
        assertEquals(0, engine.state.value.totalFlushes, "still running one millisecond out")

        advanceTimeBy(2)
        assertEquals(1, engine.state.value.totalFlushes)
        assertTrue(!engine.state.value.isFlushing, "the bowl is at rest again")
        assertNotNull(engine.state.value.message, "the app says something afterwards")
    }

    @Test
    fun `a flush plays its noise and its buzz`() = runTest {
        val audio = RecordingAudio()
        val haptics = RecordingHaptics()
        engine(audio = audio, haptics = haptics).pullHandle()

        assertEquals(listOf("play(golden=false)"), audio.calls)
        assertEquals(listOf("flush(golden=false)"), haptics.calls)
    }

    @Test
    fun `pulling mid-flush is refused, with a line about it`() = runTest {
        val haptics = RecordingHaptics()
        val engine = engine(haptics = haptics)

        engine.pullHandle()
        advanceTimeBy(1_000)
        engine.pullHandle()

        assertEquals(FlushState.Message.Kind.Busy, engine.state.value.message?.kind)
        assertTrue(haptics.calls.contains("thud"))

        advanceUntilIdle()
        assertEquals(1, engine.state.value.totalFlushes, "the second pull must not count")
    }

    @Test
    fun `a flush finishes on the duration its grade earned, not the fixture's`() = runTest {
        val engine = engine()
        engine.pullHandle(FlushGrade.Weak)
        advanceTimeBy(millis(FlushProfile.Standard, FlushGrade.Weak) + 1)
        assertEquals(1, engine.state.value.totalFlushes)
    }

    @Test
    fun `flushing nothing at all is noticed`() = runTest {
        val engine = engine()
        flush(engine)
        assertEquals(FlushState.Message.Kind.Busy, engine.state.value.message?.kind)
        // Whatever the line, it is one of the ones about not having wiped.
        assertTrue(engine.state.value.message!!.text.isNotBlank())
    }

    // Streaks

    @Test
    fun `a perfect run builds a streak and remembers the best of it`() = runTest {
        val engine = engine()
        repeat(3) { flush(engine, FlushGrade.Perfect) }
        assertEquals(3, engine.state.value.streak)
        assertEquals(3, engine.state.value.bestStreak)

        flush(engine, FlushGrade.Weak)
        assertEquals(0, engine.state.value.streak, "a weak pull ends the run")
        assertEquals(3, engine.state.value.bestStreak, "but the best of it stands")
    }

    @Test
    fun `a good pull leaves a run standing without extending it`() = runTest {
        val engine = engine()
        flush(engine, FlushGrade.Perfect)
        flush(engine, FlushGrade.Good)
        assertEquals(1, engine.state.value.streak)
    }

    @Test
    fun `a filthy bowl costs you the run whatever you do at the handle`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("grime" to 0.95)))
        assertTrue(engine.state.value.isFilthy)
        engine.pullHandle(FlushGrade.Perfect)
        assertEquals(0, engine.state.value.streak, "a perfect pull cannot save a filthy bowl")
    }

    @Test
    fun `a filthy bowl produces no gold`() = runTest {
        val engine = engine(
            settings = MapSettings(mapOf("grime" to 0.95)),
            random = QueuedRandom(0.0, 1.0),
        )
        flush(engine, FlushGrade.Perfect)
        assertEquals(0, engine.state.value.goldenFlushes)
    }

    // Gold

    @Test
    fun `a golden flush counts, celebrates, and clears up after itself`() = runTest {
        val audio = RecordingAudio()
        val engine = engine(random = QueuedRandom(0.0, 1.0), audio = audio)

        engine.pullHandle(FlushGrade.Good)
        assertTrue(engine.state.value.isGolden)
        assertTrue(engine.state.value.showsGold)
        assertEquals(listOf("play(golden=true)"), audio.calls)

        advanceTimeBy(standardFlush + 1)
        assertEquals(1, engine.state.value.goldenFlushes)
        assertNotNull(engine.state.value.celebrationStartMillis, "the gold should be falling")
        assertEquals(FlushState.Message.Kind.Golden, engine.state.value.message?.kind)

        advanceTimeBy(3_801)
        assertNull(engine.state.value.celebrationStartMillis, "and then stop falling")
        assertTrue(!engine.state.value.showsGold)
    }

    // The roll on the wall

    @Test
    fun `the roll holds five squares and no more`() = runTest {
        val engine = engine()
        engine.pullPaper(99)
        assertEquals(Upkeep.PAPER_RANGE.last, engine.state.value.paperPulled)
        engine.pullPaper(-4)
        assertEquals(0, engine.state.value.paperPulled)
    }

    @Test
    fun `a torn sheet is what goes down, and it goes down with the flush`() = runTest {
        val engine = engine()
        load(engine, 3)
        assertTrue(engine.state.value.isPaperCut)
        assertEquals(3, engine.state.value.loadedPaper)

        flush(engine)
        assertEquals(0, engine.state.value.paperPulled, "the sheet went with the water")
        assertTrue(!engine.state.value.isPaperCut, "and the roll is ready again")
    }

    @Test
    fun `the roll cannot be touched mid-flush, blocked, or once it is torn`() = runTest {
        val engine = engine()
        load(engine, 2)
        engine.pullPaper(5)
        assertEquals(2, engine.state.value.paperPulled, "a torn sheet is torn")

        val running = engine()
        running.pullHandle()
        running.pullPaper(3)
        assertEquals(0, running.state.value.paperPulled, "not while the water is moving")
    }

    @Test
    fun `an uncut sheet is a runaway, and a runaway always blocks`() = runTest {
        // The dice would say it went down; the roll disagrees.
        val engine = engine(random = QueuedRandom(1.0, 1.0))
        engine.pullPaper(2)
        assertEquals(Upkeep.RUNAWAY_PAPER, engine.state.value.loadedPaper)

        flush(engine)
        assertTrue(engine.state.value.isClogged)
        assertTrue(engine.state.value.isPaperTrailing, "still attached to the roll")
        assertEquals(2, engine.state.value.paperPulled, "the sheet is still hanging there")
        assertTrue(engine.state.value.message!!.text.startsWith("The whole roll went in."))
    }

    @Test
    fun `a trailing sheet has to be cut before the plunger will bite`() = runTest {
        val engine = engine(random = QueuedRandom(1.0, 1.0))
        engine.pullPaper(2)
        flush(engine)

        engine.plunge()
        assertEquals(0, engine.state.value.plunges, "nothing lands while it is attached")
        assertEquals("It's still attached. Cut it.", engine.state.value.message?.text)

        engine.cutPaper()
        assertTrue(!engine.state.value.isPaperTrailing)
        assertEquals(0, engine.state.value.paperPulled)
        assertEquals("Cut free. Now plunge it.", engine.state.value.message?.text)

        repeat(Upkeep.PLUNGES_TO_CLEAR) { engine.plunge() }
        assertTrue(!engine.state.value.isClogged)
        // A whole roll leaves the bowl in a state only the wand answers.
        assertTrue(engine.state.value.isFilthy, "grime ${engine.state.value.grime}")
    }

    @Test
    fun `an ordinary block swallows the sheet and costs the score, not the streak`() = runTest {
        // Every flush rolls twice, gold then clog. Two clean flushes, then the third
        // blocks: five squares makes the odds real and the dice say yes.
        val engine = engine(random = QueuedRandom(1.0, 1.0, 1.0, 1.0, 1.0, 0.0))
        flush(engine, FlushGrade.Perfect)          // banks something to lose
        load(engine, 3)
        flush(engine, FlushGrade.Perfect)
        val banked = engine.state.value.runScore
        assertTrue(banked > 0)
        assertEquals(2, engine.state.value.streak)

        load(engine, 5)
        flush(engine, FlushGrade.Perfect)

        assertTrue(engine.state.value.isClogged)
        assertTrue(!engine.state.value.isPaperTrailing, "an ordinary block is not a runaway")
        assertEquals(0, engine.state.value.paperPulled, "the sheet went in with it")
        assertEquals(3, engine.state.value.streak, "the streak deliberately survives")
        assertTrue(engine.state.value.runScore < banked, "the score paid for it")
        assertTrue(engine.state.value.lastClogCost > 0)
        assertTrue(engine.state.value.message!!.text.startsWith("Clogged. −"))
    }

    @Test
    fun `five pumps clear a blockage and stir the filth up`() = runTest {
        val engine = engine(random = QueuedRandom(1.0, 0.0))
        load(engine, 5)
        flush(engine)
        assertTrue(engine.state.value.isClogged)
        val grimeBefore = engine.state.value.grime

        repeat(Upkeep.PLUNGES_TO_CLEAR - 1) { i ->
            engine.plunge()
            assertTrue(engine.state.value.isClogged, "still blocked after ${i + 1} pumps")
        }
        engine.plunge()
        assertTrue(!engine.state.value.isClogged)
        assertEquals(grimeBefore + 0.08, engine.state.value.grime, 1e-9)
    }

    // The tank

    @Test
    fun `every flush comes off the tank and the twentieth ends the run`() = runTest {
        val engine = engine()
        repeat(Upkeep.RUN_LENGTH - 1) { flush(engine) }
        assertEquals(1, engine.state.value.flushesLeft)
        assertTrue(!engine.state.value.isRunOver)

        flush(engine)
        assertEquals(0, engine.state.value.flushesLeft)
        assertTrue(engine.state.value.isRunOver)
        assertEquals(engine.state.value.runScore, engine.state.value.bestRun, "a first tank is the best tank")
        assertTrue(engine.state.value.bestRun > 0)
    }

    @Test
    fun `a flush is worth its points times the fixture's payout`() = runTest {
        val engine = engine()
        load(engine, 3)
        flush(engine)
        assertEquals(Upkeep.points(3, golden = false), engine.state.value.runScore)

        val outhouse = engine(settings = MapSettings(mapOf("totalFlushes" to 500, "equippedFixture" to "outhouse")))
        load(outhouse, 3)
        flush(outhouse)
        assertEquals(
            (Upkeep.points(3, golden = false) * Fixture.Outhouse.payout).toInt(),
            outhouse.state.value.runScore,
            "the outhouse pays for the trouble",
        )
    }

    @Test
    fun `a dry tank refuses, and remembers being asked`() = runTest {
        val engine = engine()
        repeat(Upkeep.RUN_LENGTH) { flush(engine) }
        val total = engine.state.value.totalFlushes

        engine.pullHandle()
        assertEquals("Tank's dry. Start a new one.", engine.state.value.message?.text)
        assertEquals(1, engine.state.value.dryTankAsks)
        advanceUntilIdle()
        assertEquals(total, engine.state.value.totalFlushes, "nothing flushed")
    }

    @Test
    fun `a new tank starts clean and starts over`() = runTest {
        val engine = engine()
        load(engine, 4)
        repeat(Upkeep.RUN_LENGTH) { flush(engine) }
        assertTrue(engine.state.value.isRunOver)

        engine.startRun()
        val s = engine.state.value
        assertEquals(Upkeep.RUN_LENGTH, s.flushesLeft)
        assertEquals(0, s.runScore)
        assertTrue(!s.isRunOver)
        assertEquals(0.0, s.grime)
        assertEquals(0, s.paperPulled)
        assertTrue(s.bestRun > 0, "the best tank is kept across tanks")
    }

    @Test
    fun `scrubbing costs water, and there has to be some to spend`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("grime" to 0.5)))
        engine.useWand()
        assertEquals(0.0, engine.state.value.grime)
        assertEquals(Upkeep.RUN_LENGTH - Upkeep.WAND_COST, engine.state.value.flushesLeft)

        val dry = engine(settings = MapSettings(mapOf("grime" to 0.5)))
        repeat(Upkeep.RUN_LENGTH) { flush(dry) }
        dry.useWand()
        assertEquals("No water left to scrub with.", dry.state.value.message?.text)
    }

    // The lucky roll

    @Test
    fun `one roll in a hundred is not paper`() = runTest {
        val random = QueuedRandom().apply { cash = true }
        val engine = engine(random = random)
        engine.pullPaper(3)
        assertTrue(engine.state.value.isCashRoll)
        assertEquals("Hold on. That's not paper.", engine.state.value.message?.text)
    }

    @Test
    fun `the roll is decided once, so it cannot be fished for`() = runTest {
        val random = QueuedRandom()
        val engine = engine(random = random)
        engine.pullPaper(1)
        random.cash = true
        engine.pullPaper(3)
        engine.pullPaper(0)
        engine.pullPaper(5)
        assertTrue(!engine.state.value.isCashRoll, "yo-yoing the roll should not roll the dice again")
    }

    @Test
    fun `flushing money never blocks, pays absurdly, and shows the card`() = runTest {
        // The dice would block a five-square flush; money goes down regardless.
        val random = QueuedRandom(1.0, 0.0).apply { cash = true }
        val engine = engine(random = random)
        engine.pullPaper(5)      // uncut on purpose: money goes down however you feed it in
        flush(engine)

        val s = engine.state.value
        assertTrue(!s.isClogged, "a payout that punishes you is not a payout")
        assertEquals(
            (Upkeep.points(5, golden = false) * Upkeep.CASH_MULTIPLIER).toInt(),
            s.runScore,
        )
        assertTrue(s.isCashPayout, "Benjamin should be on screen")
        assertNotNull(s.celebrationStartMillis)
        assertEquals(FlushState.Message.Kind.Golden, s.message?.kind)
        assertTrue(!s.isCashRoll, "a plain roll goes back on the wall")

        advanceTimeBy(3_801)
        assertTrue(!engine.state.value.isCashPayout, "the card leaves with the gold")
    }

    // The daily

    @Test
    fun `starting the daily deals the day's bowl`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("grime" to 0.6)))
        engine.startDaily()
        val s = engine.state.value
        assertTrue(s.isDailyRunning)
        assertEquals(Fixture.Outhouse, s.fixture, "today is the outhouse")
        assertEquals(0.02, s.grime, 1e-12)
        assertEquals(0, s.streak)
        assertEquals("Daily #76 — 4 squares", s.message?.text)
    }

    @Test
    fun `the daily is five flushes, does not touch the tank, and pays for hitting the target`() = runTest {
        val engine = engine()
        engine.startDaily()
        val tank = engine.state.value.flushesLeft

        load(engine, 4)                              // the target
        flush(engine)
        val run = engine.state.value.daily!!
        assertEquals(listOf(DailyMark.Good), run.marks)
        assertEquals((Upkeep.points(4, false) * DailyChallenge.TARGET_BONUS).toInt(), run.score)
        assertEquals(tank, engine.state.value.flushesLeft, "the tank is not in play during a daily")

        load(engine, 2)                              // off target: no bonus
        flush(engine, FlushGrade.Perfect)
        assertEquals(DailyMark.Perfect, engine.state.value.daily!!.marks.last())
        assertEquals(run.score + Upkeep.points(2, false), engine.state.value.daily!!.score)
    }

    @Test
    fun `a blocked daily flush is marked and scores nothing`() = runTest {
        val engine = engine(random = QueuedRandom(1.0, 0.0))
        engine.startDaily()
        load(engine, 5)
        flush(engine)
        val run = engine.state.value.daily!!
        assertEquals(listOf(DailyMark.Clogged), run.marks)
        assertEquals(0, run.score)
        assertEquals(0, engine.state.value.lastClogCost, "a daily deducts nothing from a tank it is not using")
    }

    @Test
    fun `finishing the daily hands the bowl back and keeps the result`() = runTest {
        val settings = MapSettings(mapOf("grime" to 0.3, "totalFlushes" to 200, "equippedFixture" to "victorian"))
        val engine = engine(settings = settings)
        engine.startDaily()
        assertEquals(Fixture.Outhouse, engine.state.value.fixture)

        repeat(DailyChallenge.FLUSH_COUNT) { flush(engine) }

        val s = engine.state.value
        assertTrue(s.isDailyDone)
        assertTrue(s.message!!.text.startsWith("Daily done —"))
        assertEquals(Fixture.Victorian, s.fixture, "ordinary play gets its toilet back")
        assertEquals(0.3, s.grime, 1e-12, "and its grime")

        // Done means done: no second attempt, and the handle still works.
        engine.startDaily()
        assertEquals(DailyChallenge.FLUSH_COUNT, engine.state.value.daily!!.marks.size)
        flush(engine)
        assertEquals(200 + DailyChallenge.FLUSH_COUNT + 1, engine.state.value.totalFlushes, "play goes on")
    }

    @Test
    fun `a finished daily survives a restart, on the same day`() = runTest {
        val settings = MapSettings()
        val first = engine(settings = settings)
        first.startDaily()
        repeat(DailyChallenge.FLUSH_COUNT) { flush(first) }

        val sameDay = engine(settings = settings)
        assertTrue(sameDay.state.value.isDailyDone)

        val tomorrow = engine(settings = settings, clock = FakeClock(noon + 86_400_000))
        assertNull(tomorrow.state.value.daily, "yesterday's attempt is not today's")
        assertEquals(9_377, tomorrow.state.value.challenge.stamp)
    }

    @Test
    fun `the daily cannot start mid-flush or on a blocked bowl`() = runTest {
        val engine = engine()
        engine.pullHandle()
        engine.startDaily()
        assertNull(engine.state.value.daily)
    }

    // Fixtures

    @Test
    fun `a fixture you have not earned is refused, silently`() = runTest {
        val engine = engine()
        engine.equip(Fixture.Orbital)
        assertEquals(Fixture.Standard, engine.state.value.fixture)
        assertNull(engine.state.value.message)
    }

    @Test
    fun `an earned fixture is installed and introduces itself`() = runTest {
        val audio = RecordingAudio()
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 500)), audio = audio)
        engine.equip(Fixture.Chrome)
        assertEquals(Fixture.Chrome, engine.state.value.fixture)
        assertEquals(Fixture.Chrome.blurb, engine.state.value.message?.text)
        assertTrue(audio.calls.any { it.startsWith("prepare") })
    }

    @Test
    fun `unlocking a toilet outranks anything else the app had to say`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 24)))
        load(engine, 2)
        flush(engine)
        assertEquals(25, engine.state.value.totalFlushes)
        assertEquals("Unlocked — ${Fixture.Outhouse.name}", engine.state.value.message?.text)
    }

    // Persistence

    @Test
    fun `the tally and the best tank survive a restart`() = runTest {
        val settings = MapSettings()
        val first = engine(settings = settings)
        load(first, 4)
        repeat(Upkeep.RUN_LENGTH) { flush(first, FlushGrade.Perfect) }

        val second = engine(settings = settings)
        assertEquals(first.state.value.totalFlushes, second.state.value.totalFlushes)
        assertEquals(first.state.value.bestStreak, second.state.value.bestStreak)
        assertEquals(first.state.value.bestRun, second.state.value.bestRun)
        assertEquals(first.state.value.grime, second.state.value.grime, 1e-9)
        assertEquals(first.state.value.standings, second.state.value.standings)
        // The tank itself is per session: a restart is a fresh one.
        assertEquals(Upkeep.RUN_LENGTH, second.state.value.flushesLeft)
    }

    @Test
    fun `a saved fixture you can no longer afford falls back to the standard one`() = runTest {
        val settings = MapSettings(mapOf("equippedFixture" to "orbital", "totalFlushes" to 3))
        assertEquals(Fixture.Standard, engine(settings = settings).state.value.fixture)
    }

    // Reset

    @Test
    fun `reset wipes the tally, the tank, the roll and the daily`() = runTest {
        val settings = MapSettings(mapOf("totalFlushes" to 500, "equippedFixture" to "chrome"))
        val engine = engine(settings = settings)
        engine.startDaily()
        load(engine, 3)
        flush(engine, FlushGrade.Perfect)

        engine.resetStats()

        val s = engine.state.value
        assertEquals(0, s.totalFlushes)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.bestRun)
        assertEquals(Upkeep.RUN_LENGTH, s.flushesLeft)
        assertEquals(0, s.paperPulled)
        assertNull(s.daily)
        assertEquals(Fixture.Standard, s.fixture)
        assertEquals(Standings(), s.standings)
        assertEquals(0, engine(settings = settings).state.value.totalFlushes, "and it is really gone")
    }

    @Test
    fun `a reset is not undone by the settle that was already in flight`() = runTest {
        val engine = engine()
        engine.pullHandle()
        advanceTimeBy(standardFlush - 50)
        engine.resetStats()
        advanceUntilIdle()
        assertEquals(0, engine.state.value.totalFlushes)
    }

    // The board

    @Test
    fun `a flush lands on today's row with what it was worth`() = runTest {
        val engine = engine()
        load(engine, 3)
        flush(engine)
        val today = engine.state.value.standings.today(noon, utc)
        assertNotNull(today)
        assertEquals(1, today.flushes)
        assertEquals(Upkeep.points(3, golden = false), today.score)
    }

    @Test
    fun `a runaway is worth nothing on the board`() = runTest {
        val engine = engine(random = QueuedRandom(1.0, 1.0))
        engine.pullPaper(2)
        flush(engine)
        assertEquals(0, engine.state.value.standings.today(noon, utc)!!.score)
    }
}
