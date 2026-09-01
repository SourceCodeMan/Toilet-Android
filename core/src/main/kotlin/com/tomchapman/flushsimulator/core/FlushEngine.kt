package com.tomchapman.flushsimulator.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.random.Random

/**
 * Owns the one thing this app does.
 *
 * The visuals are driven entirely by [FlushState.flushStartMillis] plus
 * [FlushTimeline], so the engine's only real jobs are starting the noise, keeping the
 * tally, and picking something to say when the water settles.
 *
 * Everything it cannot do without — storage, sound, buzz, the clock and the dice —
 * arrives through the constructor, so the whole engine runs in a unit test.
 *
 * Call it from one thread. The Swift had `@MainActor` to enforce that; Kotlin has no
 * equivalent, so it is a contract rather than a guarantee: [scope] is expected to be
 * main-confined (`viewModelScope`), which is where every call from Compose already
 * comes from. There are no locks here because there is no concurrency to protect
 * against — only the counters and jobs below, which one thread owns.
 */
class FlushEngine(
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val audio: FlushAudio = FlushAudio.None,
    private val haptics: Haptics = Haptics.None,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
    private val quips: Quips = Quips(random),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val _state = MutableStateFlow(restore(settings))
    val state: StateFlow<FlushState> = _state.asStateFlow()

    private var flushJob: Job? = null
    private var messageJob: Job? = null
    private var celebrationJob: Job? = null

    /**
     * Bumped whenever a flush is started or thrown away. A settle that belongs to an
     * older generation has been overtaken and must not write anything back.
     */
    private var generation = 0

    private var lastMessageId = 0L

    // The button

    fun pullHandle(pulled: FlushGrade = FlushGrade.Good) {
        val s = _state.value

        if (s.isClogged) {
            haptics.thud()
            show("Blocked. Plunge it.", FlushState.Message.Kind.Busy)
            return
        }
        if (s.isFlushing) {
            haptics.thud()
            show(quips.busyLine(), FlushState.Message.Kind.Busy)
            return
        }

        // Settle the streak before the odds are rolled, so a perfect pull pays out on
        // the flush that earned it rather than the next one.
        var streak = s.streak
        var bestStreak = s.bestStreak
        when {
            // Nothing you do at the handle survives a bowl in this state.
            s.isFilthy -> streak = 0
            pulled.keepsStreak -> {
                streak += 1
                if (streak > bestStreak) {
                    bestStreak = streak
                    settings.putInt(Key.BEST_STREAK, bestStreak)
                }
            }
            pulled.breaksStreak -> streak = 0
        }

        val golden = random.nextDouble() < Upkeep.goldenChance(streak, s.grime)

        messageJob?.cancel()
        _state.value = s.copy(
            grade = pulled,
            streak = streak,
            bestStreak = bestStreak,
            isGolden = golden,
            flushStartMillis = clock.nowMillis(),
            message = null,
        )

        // The picture is graded, the noise and the buzz are not: both are cached per
        // fixture, and a cistern refills in its own time however you pulled the lever.
        audio.play(golden, s.fixture.profile)
        haptics.flush(golden, s.fixture.profile.timeScale)

        flushJob?.cancel()
        generation += 1
        val duration = pulled.applyTo(s.fixture.profile).duration
        val mine = generation
        flushJob = scope.launch {
            delay((duration * 1_000).toLong())
            settle(mine)
        }
    }

    fun resetStats() {
        // A flush settles asynchronously. Cancelling is not enough on its own, so
        // moving the generation on is what actually invalidates a settle already in
        // flight.
        flushJob?.cancel()
        flushJob = null
        generation += 1

        celebrationJob?.cancel()
        celebrationJob = null
        audio.stop()

        settings.putInt(Key.TOTAL, 0)
        settings.putInt(Key.GOLDEN, 0)
        settings.putInt(Key.BEST_STREAK, 0)
        settings.putDouble(Key.GRIME, 0.0)
        settings.putInt(Key.PAPER, Upkeep.DEFAULT_PAPER)
        // The standard toilet is the only one left standing after a wipe.
        settings.putString(Key.FIXTURE, Fixture.Standard.id)
        Standings.clear(settings)

        // Every default on FlushState is already what a wiped save reads back as.
        _state.value = FlushState()
        show("A clean slate. Literally.", FlushState.Message.Kind.Quip)
    }

    // Fixtures

    /**
     * Install a fixture. Silently refuses one that has not been earned, and says so
     * about one asked for mid-flush.
     */
    fun equip(new: Fixture) {
        val s = _state.value
        if (s.totalFlushes < new.unlockAt || new == s.fixture) return

        // Swapping mid-flush would pull the profile out from under the running
        // animation while the flush still ends on the old fixture's duration.
        if (s.isFlushing) {
            haptics.thud()
            show("Not mid-flush.", FlushState.Message.Kind.Busy)
            return
        }

        _state.update { it.copy(fixture = new) }
        settings.putString(Key.FIXTURE, new.id)
        audio.prepare(new.profile)
        haptics.thud()
        show(new.blurb, FlushState.Message.Kind.Unlock)
    }

    // Upkeep

    fun setPaper(squares: Int) {
        val clamped = squares.coerceIn(Upkeep.PAPER_RANGE.first, Upkeep.PAPER_RANGE.last)
        if (clamped == _state.value.paper) return
        settings.putInt(Key.PAPER, clamped)
        _state.update { it.copy(paper = clamped) }
    }

    /** Scrub it. Costs you nothing but the time, and a clean bowl flushes gold more often. */
    fun useWand() {
        val s = _state.value
        if (s.isFlushing || s.grime <= 0) return

        val wasFilthy = s.grime >= Upkeep.GRIMY_ABOVE
        settings.putDouble(Key.GRIME, 0.0)
        _state.update { it.copy(grime = 0.0) }
        haptics.tick()
        show(
            if (wasFilthy) "Spotless. That was overdue." else "Spotless.",
            FlushState.Message.Kind.Unlock,
        )
    }

    /** One pump. Five clears it. */
    fun plunge() {
        val s = _state.value
        if (!s.isClogged) return

        val plunges = s.plunges + 1
        haptics.thud()

        if (plunges < Upkeep.PLUNGES_TO_CLEAR) {
            _state.update { it.copy(plunges = plunges) }
            show("${Upkeep.PLUNGES_TO_CLEAR - plunges} more", FlushState.Message.Kind.Busy)
            return
        }

        // Clearing a blockage churns the filth up rather than removing it.
        val grime = minOf(s.grime + 0.08, 1.0)
        settings.putDouble(Key.GRIME, grime)
        _state.update { it.copy(isClogged = false, plunges = 0, grime = grime) }
        show("Cleared. Try using less next time.", FlushState.Message.Kind.Milestone)
    }

    // Aftermath

    private fun settle(mine: Int) {
        // Overtaken by a reset, or by a flush that started after this one.
        if (mine != generation) return

        val s = _state.value
        val before = s.totalFlushes
        val total = before + 1
        settings.putInt(Key.TOTAL, total)

        val goldenTotal = if (s.isGolden) s.goldenFlushes + 1 else s.goldenFlushes
        if (s.isGolden) settings.putInt(Key.GOLDEN, goldenTotal)

        val now = clock.nowMillis()
        val standings = s.standings.record(
            golden = s.isGolden,
            streak = s.streak,
            points = Upkeep.points(s.paper, s.isGolden),
            atMillis = now,
            zone = zone,
        )
        standings.save(settings)

        // Every flush leaves a little behind, and paper leaves more.
        val grime = (s.grime + Upkeep.GRIME_PER_FLUSH * (1 + s.paper * 0.25)).coerceIn(0.0, 1.0)
        settings.putDouble(Key.GRIME, grime)

        // Then find out whether it went down at all.
        val clogged = random.nextDouble() < Upkeep.clogChance(
            paper = s.paper,
            grime = grime,
            grade = s.grade,
            tolerance = s.fixture.tolerance,
        )

        _state.value = s.copy(
            flushStartMillis = null,
            totalFlushes = total,
            goldenFlushes = goldenTotal,
            standings = standings,
            grime = grime,
            isClogged = clogged,
            plunges = if (clogged) 0 else s.plunges,
            streak = if (clogged) 0 else s.streak,
        )

        if (s.isGolden) celebrate(now)

        if (clogged) {
            haptics.thud()
            show("Clogged.", FlushState.Message.Kind.Busy)
            return
        }

        // Earning a new toilet outranks anything else the app had to say. The gold
        // still happens on screen, it just does not get the line.
        val earned = Fixture.all.firstOrNull { it.unlockAt in (before + 1)..total }
        val milestone = Quips.milestone(total)

        when {
            earned != null ->
                show("Unlocked — ${earned.name}", FlushState.Message.Kind.Unlock)
            s.isGolden ->
                show(quips.goldenLine(), FlushState.Message.Kind.Golden)
            s.grade == FlushGrade.Perfect && s.streak >= 2 ->
                show("Perfect ×${s.streak}", FlushState.Message.Kind.Milestone)
            s.grade == FlushGrade.Weak || s.grade == FlushGrade.Overheld ->
                show(s.grade.label, FlushState.Message.Kind.Busy)
            grime >= Upkeep.FILTHY_ABOVE ->
                show("Too filthy. No streak, no gold.", FlushState.Message.Kind.Busy)
            grime >= Upkeep.GRIMY_ABOVE ->
                show("That bowl needs a wand.", FlushState.Message.Kind.Busy)
            milestone != null ->
                show(milestone, FlushState.Message.Kind.Milestone)
            else ->
                show(quips.afterFlushLine(), FlushState.Message.Kind.Quip)
        }
    }

    private fun celebrate(startedAt: Long) {
        _state.update { it.copy(celebrationStartMillis = startedAt) }
        celebrationJob?.cancel()
        celebrationJob = scope.launch {
            // Longer than the slowest flake takes to fall (3.75s), so the gold lands
            // rather than blinking out mid-air.
            delay(CELEBRATION_MILLIS)
            _state.update { it.copy(celebrationStartMillis = null) }
        }
    }

    private fun show(text: String, kind: FlushState.Message.Kind) {
        val id = ++lastMessageId
        _state.update { it.copy(message = FlushState.Message(id, text, kind)) }
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(MESSAGE_MILLIS)
            _state.update { if (it.message?.id == id) it.copy(message = null) else it }
        }
    }

    private object Key {
        const val TOTAL = "totalFlushes"
        const val GOLDEN = "goldenFlushes"
        const val FIXTURE = "equippedFixture"
        const val BEST_STREAK = "bestStreak"
        const val GRIME = "grime"
        const val PAPER = "paper"
    }

    private companion object {
        const val MESSAGE_MILLIS = 2_900L
        const val CELEBRATION_MILLIS = 3_800L

        /** What a saved game reads back as. */
        fun restore(settings: Settings): FlushState {
            val total = settings.getInt(Key.TOTAL)

            // Fall back to the standard toilet if the saved one is unknown, or if the
            // tally was wiped and it is no longer earned.
            val saved = Fixture.withId(settings.getString(Key.FIXTURE) ?: Fixture.Standard.id)

            return FlushState(
                totalFlushes = total,
                goldenFlushes = settings.getInt(Key.GOLDEN),
                bestStreak = settings.getInt(Key.BEST_STREAK),
                // Both clamped: a save can be edited on a rooted device, and loading
                // is the boundary where that gets caught rather than at every use.
                grime = settings.getDouble(Key.GRIME).coerceIn(0.0, 1.0),
                paper = settings.getInt(Key.PAPER, Upkeep.DEFAULT_PAPER)
                    .coerceIn(Upkeep.PAPER_RANGE.first, Upkeep.PAPER_RANGE.last),
                standings = Standings.load(settings),
                fixture = if (total >= saved.unlockAt) saved else Fixture.Standard,
            )
        }
    }
}
