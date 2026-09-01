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
 * to a settle already in flight) are ordinary assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlushEngineTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val standardFlushMillis = (FlushProfile.Standard.duration * 1_000).toLong()

    private fun TestScope.engine(
        settings: Settings = MapSettings(),
        random: Random = QueuedRandom(),
        audio: FlushAudio = RecordingAudio(),
        haptics: Haptics = RecordingHaptics(),
        clock: Clock = FakeClock(),
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

    // Starting from nothing

    @Test
    fun `a fresh engine starts at nothing`() = runTest {
        val state = engine().state.value
        assertEquals(0, state.totalFlushes)
        assertEquals(0, state.goldenFlushes)
        assertEquals(0, state.streak)
        assertEquals(0.0, state.grime)
        assertEquals(Upkeep.DEFAULT_PAPER, state.paper)
        assertEquals(Fixture.Standard, state.fixture)
        assertTrue(!state.isFlushing)
        assertTrue(!state.isClogged)
        assertNull(state.message)
    }

    // One flush

    @Test
    fun `nothing is tallied until the water settles`() = runTest {
        val engine = engine()
        engine.pullHandle(FlushGrade.Good)

        assertTrue(engine.state.value.isFlushing)
        assertEquals(0, engine.state.value.totalFlushes, "the flush is still running")

        advanceTimeBy(standardFlushMillis - 1)
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

        assertEquals(
            FlushState.Message.Kind.Busy,
            engine.state.value.message?.kind,
            "the second pull should be told to wait",
        )
        assertTrue(haptics.calls.contains("thud"))

        advanceUntilIdle()
        assertEquals(1, engine.state.value.totalFlushes, "the second pull must not count")
    }

    @Test
    fun `a flush finishes on the duration its grade earned, not the fixture's`() = runTest {
        // A weak pull is 0.72 of the fixture's length; settling on the fixture's own
        // duration would leave the bowl visibly at rest while the tally waited.
        val engine = engine()
        engine.pullHandle(FlushGrade.Weak)

        val weakMillis = (FlushProfile.Standard.duration * 0.72 * 1_000).toLong()
        advanceTimeBy(weakMillis + 1)
        assertEquals(1, engine.state.value.totalFlushes)
    }

    // Streaks

    @Test
    fun `a perfect run builds a streak and remembers the best of it`() = runTest {
        val engine = engine()
        repeat(3) {
            engine.pullHandle(FlushGrade.Perfect)
            advanceUntilIdle()
        }
        assertEquals(3, engine.state.value.streak)
        assertEquals(3, engine.state.value.bestStreak)

        engine.pullHandle(FlushGrade.Weak)
        advanceUntilIdle()
        assertEquals(0, engine.state.value.streak, "a weak pull ends the run")
        assertEquals(3, engine.state.value.bestStreak, "but the best of it stands")
    }

    @Test
    fun `a good pull leaves a run standing without extending it`() = runTest {
        val engine = engine()
        engine.pullHandle(FlushGrade.Perfect)
        advanceUntilIdle()
        engine.pullHandle(FlushGrade.Good)
        advanceUntilIdle()
        assertEquals(1, engine.state.value.streak)
    }

    @Test
    fun `holding too long ends a run`() = runTest {
        val engine = engine()
        engine.pullHandle(FlushGrade.Perfect)
        advanceUntilIdle()
        engine.pullHandle(FlushGrade.Overheld)
        advanceUntilIdle()
        assertEquals(0, engine.state.value.streak)
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
        // The dice would say yes to anything; the bowl says no.
        val engine = engine(
            settings = MapSettings(mapOf("grime" to 0.95)),
            random = QueuedRandom(0.0, 1.0),
        )
        engine.pullHandle(FlushGrade.Perfect)
        advanceUntilIdle()
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

        advanceTimeBy(standardFlushMillis + 1)
        assertEquals(1, engine.state.value.goldenFlushes)
        assertNotNull(engine.state.value.celebrationStartMillis, "the gold should be falling")
        assertEquals(FlushState.Message.Kind.Golden, engine.state.value.message?.kind)

        advanceTimeBy(3_801)
        assertNull(engine.state.value.celebrationStartMillis, "and then stop falling")
        assertTrue(!engine.state.value.showsGold)
    }

    // Clogs

    @Test
    fun `a clog stops everything until it is plunged`() = runTest {
        val haptics = RecordingHaptics()
        // No gold, then a clog. Five squares makes the odds real.
        val engine = engine(random = QueuedRandom(1.0, 0.0), haptics = haptics)
        engine.setPaper(5)

        engine.pullHandle(FlushGrade.Perfect)
        advanceTimeBy(standardFlushMillis + 1)

        assertTrue(engine.state.value.isClogged)
        assertEquals(0, engine.state.value.streak, "a blockage costs you the run")
        assertEquals("Clogged.", engine.state.value.message?.text)

        val tally = engine.state.value.totalFlushes
        engine.pullHandle()
        assertEquals("Blocked. Plunge it.", engine.state.value.message?.text)
        advanceUntilIdle()
        assertEquals(tally, engine.state.value.totalFlushes, "a blocked bowl does not flush")
    }

    @Test
    fun `five pumps clear a blockage and stir the filth up`() = runTest {
        val engine = engine(random = QueuedRandom(1.0, 0.0))
        engine.setPaper(5)
        engine.pullHandle(FlushGrade.Perfect)
        advanceTimeBy(standardFlushMillis + 1)
        assertTrue(engine.state.value.isClogged)

        val grimeBefore = engine.state.value.grime

        repeat(Upkeep.PLUNGES_TO_CLEAR - 1) { i ->
            engine.plunge()
            assertTrue(engine.state.value.isClogged, "still blocked after ${i + 1} pumps")
            assertEquals("${Upkeep.PLUNGES_TO_CLEAR - i - 1} more", engine.state.value.message?.text)
        }

        engine.plunge()
        assertTrue(!engine.state.value.isClogged)
        assertEquals(0, engine.state.value.plunges)
        assertEquals(grimeBefore + 0.08, engine.state.value.grime, 1e-9, "clearing it churns the filth up")
    }

    @Test
    fun `plunging a bowl that is not blocked does nothing`() = runTest {
        val engine = engine()
        engine.plunge()
        assertEquals(0, engine.state.value.plunges)
        assertNull(engine.state.value.message)
    }

    // Upkeep

    @Test
    fun `flushing dirties the bowl, and paper dirties it faster`() = runTest {
        val plain = engine()
        plain.setPaper(0)
        plain.pullHandle()
        advanceUntilIdle()

        val heavy = engine()
        heavy.setPaper(5)
        heavy.pullHandle()
        advanceUntilIdle()

        assertTrue(plain.state.value.grime > 0.0)
        assertTrue(heavy.state.value.grime > plain.state.value.grime, "five squares should leave more behind")
    }

    @Test
    fun `the wand wipes the bowl clean`() = runTest {
        val haptics = RecordingHaptics()
        val engine = engine(settings = MapSettings(mapOf("grime" to 0.7)), haptics = haptics)

        engine.useWand()
        assertEquals(0.0, engine.state.value.grime)
        assertEquals("Spotless. That was overdue.", engine.state.value.message?.text)
        assertTrue(haptics.calls.contains("tick"))
    }

    @Test
    fun `the wand says less about a bowl that was barely dirty`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("grime" to 0.1)))
        engine.useWand()
        assertEquals("Spotless.", engine.state.value.message?.text)
    }

    @Test
    fun `the wand does nothing to a clean bowl, or to one mid-flush`() = runTest {
        val clean = engine()
        clean.useWand()
        assertNull(clean.state.value.message)

        val busy = engine(settings = MapSettings(mapOf("grime" to 0.5)))
        busy.pullHandle()
        busy.useWand()
        assertEquals(0.5, busy.state.value.grime, "you cannot scrub a bowl that is flushing")
    }

    @Test
    fun `paper is clamped to what the roll holds`() = runTest {
        val engine = engine()
        engine.setPaper(99)
        assertEquals(Upkeep.PAPER_RANGE.last, engine.state.value.paper)
        engine.setPaper(-4)
        assertEquals(Upkeep.PAPER_RANGE.first, engine.state.value.paper)
    }

    // Fixtures

    @Test
    fun `a fixture you have not earned is refused, silently`() = runTest {
        val engine = engine()
        engine.equip(Fixture.Orbital)
        assertEquals(Fixture.Standard, engine.state.value.fixture)
        assertNull(engine.state.value.message, "no need to rub it in")
    }

    @Test
    fun `an earned fixture is installed and introduces itself`() = runTest {
        val audio = RecordingAudio()
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 500)), audio = audio)

        engine.equip(Fixture.Chrome)
        assertEquals(Fixture.Chrome, engine.state.value.fixture)
        assertEquals(Fixture.Chrome.blurb, engine.state.value.message?.text)
        assertTrue(audio.calls.any { it.startsWith("prepare") }, "the new voice should be rendered")
    }

    @Test
    fun `fixtures cannot be swapped mid-flush`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 500)))
        engine.pullHandle()
        engine.equip(Fixture.Chrome)

        assertEquals(Fixture.Standard, engine.state.value.fixture)
        assertEquals("Not mid-flush.", engine.state.value.message?.text)
    }

    @Test
    fun `unlocking a toilet outranks anything else the app had to say`() = runTest {
        // 24 flushes in the bank, and the 25th earns the outhouse.
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 24)))
        engine.pullHandle(FlushGrade.Good)
        advanceTimeBy(standardFlushMillis + 1)

        assertEquals(25, engine.state.value.totalFlushes)
        assertEquals("Unlocked — ${Fixture.Outhouse.name}", engine.state.value.message?.text)
        assertEquals(FlushState.Message.Kind.Unlock, engine.state.value.message?.kind)
    }

    @Test
    fun `a milestone gets its line when nothing better is happening`() = runTest {
        val engine = engine(settings = MapSettings(mapOf("totalFlushes" to 9)))
        engine.pullHandle(FlushGrade.Good)
        advanceTimeBy(standardFlushMillis + 1)
        assertEquals(Quips.milestone(10), engine.state.value.message?.text)
    }

    // Persistence

    @Test
    fun `the tally survives a restart`() = runTest {
        val settings = MapSettings()
        val first = engine(settings = settings)
        first.setPaper(4)
        repeat(2) {
            first.pullHandle(FlushGrade.Perfect)
            advanceUntilIdle()
        }

        val second = engine(settings = settings)
        assertEquals(first.state.value.totalFlushes, second.state.value.totalFlushes)
        assertEquals(first.state.value.bestStreak, second.state.value.bestStreak)
        assertEquals(first.state.value.grime, second.state.value.grime, 1e-9)
        assertEquals(4, second.state.value.paper)
        assertEquals(first.state.value.standings, second.state.value.standings)
    }

    @Test
    fun `a saved fixture you can no longer afford falls back to the standard one`() = runTest {
        val settings = MapSettings(mapOf("equippedFixture" to "orbital", "totalFlushes" to 3))
        assertEquals(Fixture.Standard, engine(settings = settings).state.value.fixture)
    }

    @Test
    fun `an unknown saved fixture falls back to the standard one`() = runTest {
        val settings = MapSettings(mapOf("equippedFixture" to "gold-plated-nonsense", "totalFlushes" to 9_999))
        assertEquals(Fixture.Standard, engine(settings = settings).state.value.fixture)
    }

    @Test
    fun `a saved fixture you have earned comes back installed`() = runTest {
        val settings = MapSettings(mapOf("equippedFixture" to "victorian", "totalFlushes" to 150))
        assertEquals(Fixture.Victorian, engine(settings = settings).state.value.fixture)
    }

    // Reset

    @Test
    fun `reset wipes the tally, the board and the fixture`() = runTest {
        val settings = MapSettings(mapOf("totalFlushes" to 500, "equippedFixture" to "chrome"))
        val audio = RecordingAudio()
        val engine = engine(settings = settings, audio = audio)
        engine.pullHandle(FlushGrade.Perfect)
        advanceUntilIdle()

        engine.resetStats()

        val state = engine.state.value
        assertEquals(0, state.totalFlushes)
        assertEquals(0, state.goldenFlushes)
        assertEquals(0, state.bestStreak)
        assertEquals(0.0, state.grime)
        assertEquals(Upkeep.DEFAULT_PAPER, state.paper)
        assertEquals(Fixture.Standard, state.fixture)
        assertEquals(Standings(), state.standings)
        assertTrue(audio.calls.contains("stop"))

        // And it is really gone, not just gone from the screen.
        assertEquals(0, engine(settings = settings).state.value.totalFlushes)
    }

    @Test
    fun `a reset is not undone by the settle that was already in flight`() = runTest {
        // The subtle one: cancelling the job is not enough on its own, which is why
        // the engine carries a generation counter.
        val engine = engine()
        engine.pullHandle()
        advanceTimeBy(standardFlushMillis - 50)

        engine.resetStats()
        advanceUntilIdle()

        assertEquals(0, engine.state.value.totalFlushes, "the abandoned flush must not write itself back")
        assertTrue(!engine.state.value.isFlushing)
    }

    @Test
    fun `a message clears itself after its moment`() = runTest {
        val engine = engine()
        engine.pullHandle()
        advanceTimeBy(standardFlushMillis + 1)
        assertNotNull(engine.state.value.message)

        advanceTimeBy(2_901)
        assertNull(engine.state.value.message)
    }

    @Test
    fun `a new flush clears whatever the last one said`() = runTest {
        val engine = engine()
        engine.pullHandle()
        advanceTimeBy(standardFlushMillis + 1)
        assertNotNull(engine.state.value.message)

        engine.pullHandle()
        assertNull(engine.state.value.message, "the old line goes when the water moves")
    }

    // The board

    @Test
    fun `a flush lands on today's row with what it was worth`() = runTest {
        val clock = FakeClock(1_788_264_000_000L)   // 2026-09-01T12:00Z
        val engine = engine(clock = clock)
        engine.setPaper(3)
        engine.pullHandle(FlushGrade.Good)
        advanceUntilIdle()

        val today = engine.state.value.standings.today(clock.millis, utc)
        assertNotNull(today)
        assertEquals(1, today.flushes)
        assertEquals(Upkeep.points(3, golden = false), today.score)
    }
}
