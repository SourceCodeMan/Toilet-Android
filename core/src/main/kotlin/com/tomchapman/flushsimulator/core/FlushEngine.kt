package com.tomchapman.flushsimulator.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.ZoneId
import kotlin.math.roundToInt
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

    /** Today's puzzle. Derived from the date, so it needs no network. */
    val challenge: DailyChallenge = DailyChallenge.today(clock.nowMillis(), zone)

    private val _state = MutableStateFlow(restore(settings, challenge))
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

    /**
     * Whether this sheet has had its chance yet, so the roll cannot be yo-yoed until
     * it pays out.
     */
    private var cashRolled = false

    /** Whether the blockage was a whole roll rather than an ordinary overload. */
    private var wasRunaway = false

    /** What the bowl looked like before the daily took it over. */
    private var fixtureBeforeDaily: Fixture? = null
    private var grimeBeforeDaily: Double? = null

    // The button

    /**
     * The finger has gone down on the lever.
     *
     * Nothing happens yet but the feel of it. It lives here rather than in the view so
     * that the engine stays the only thing holding the haptics.
     */
    fun handleTouched() {
        haptics.tick()
    }

    fun pullHandle(pulled: FlushGrade = FlushGrade.Good) {
        val s = _state.value

        // A dry tank is the end of the run, not a soft nudge.
        if (!(s.isDailyRunning || s.flushesLeft > 0)) {
            haptics.thud()
            _state.update { it.copy(dryTankAsks = it.dryTankAsks + 1) }
            show("Tank's dry. Start a new one.", FlushState.Message.Kind.Busy)
            return
        }
        if (s.isDailyDone && fixtureBeforeDaily != null) {
            haptics.thud()
            show("Today's daily is done. Come back tomorrow.", FlushState.Message.Kind.Busy)
            return
        }
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
        settings.putInt(Key.BEST_RUN, 0)
        settings.putDouble(Key.GRIME, 0.0)
        // The standard toilet is the only one left standing after a wipe.
        settings.putString(Key.FIXTURE, Fixture.Standard.id)
        Standings.clear(settings)
        DailyResult.clear(settings)

        cashRolled = false
        wasRunaway = false
        fixtureBeforeDaily = null
        grimeBeforeDaily = null

        // Every default on FlushState is already what a wiped save reads back as.
        _state.value = FlushState(challenge = challenge)
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

    // The tank

    /** Draw a fresh tank and a clean bowl. */
    fun startRun() {
        settings.putDouble(Key.GRIME, 0.0)
        _state.update {
            it.copy(
                flushesLeft = Upkeep.RUN_LENGTH,
                runScore = 0,
                isRunOver = false,
                streak = 0,
                grime = 0.0,
                paperPulled = 0,
                isPaperCut = false,
                isPaperTrailing = false,
                isClogged = false,
                plunges = 0,
            )
        }
        show("A full tank. ${Upkeep.RUN_LENGTH} flushes.", FlushState.Message.Kind.Unlock)
    }

    /** Take one flush off the tank and bank what it was worth. */
    private fun spendFromTank(s: FlushState, blocked: Boolean, load: Int, cash: Boolean): FlushState {
        val flushesLeft = (s.flushesLeft - 1).coerceAtLeast(0)

        val worth = (
            Upkeep.points(load, s.isGolden) * s.fixture.payout *
                (if (cash) Upkeep.CASH_MULTIPLIER else 1.0)
            ).roundToInt()

        var runScore = s.runScore
        var lastClogCost = s.lastClogCost
        if (blocked) {
            // A block costs what the flush would have paid. That scales with what you
            // put in, so it is greed being punished rather than luck — and it lands on
            // the score, which the bowl controls, instead of the streak, which the
            // handle earned.
            lastClogCost = minOf(worth, runScore)
            runScore = (runScore - worth).coerceAtLeast(0)
        } else {
            runScore += worth
        }

        var bestRun = s.bestRun
        var isRunOver = s.isRunOver
        if (flushesLeft == 0) {
            isRunOver = true
            if (runScore > bestRun) {
                bestRun = runScore
                settings.putInt(Key.BEST_RUN, bestRun)
            }
        }
        return s.copy(
            flushesLeft = flushesLeft,
            runScore = runScore,
            bestRun = bestRun,
            isRunOver = isRunOver,
            lastClogCost = lastClogCost,
        )
    }

    // The daily

    /**
     * Take today's puzzle. Sets the bowl up the way the date says, and remembers what
     * was there so ordinary play gets it back afterwards.
     */
    fun startDaily() {
        val s = _state.value
        if (s.isDailyRunning || s.isDailyDone || s.isFlushing || s.isClogged) return

        fixtureBeforeDaily = s.fixture
        grimeBeforeDaily = s.grime

        settings.putDouble(Key.GRIME, challenge.startingGrime)
        _state.value = s.copy(
            daily = DailyResult(challenge.stamp),
            fixture = challenge.fixture,
            grime = challenge.startingGrime,
            paperPulled = 0,
            isPaperCut = false,
            isPaperTrailing = false,
            streak = 0,
        )

        audio.prepare(challenge.fixture.profile)
        show(
            "Daily #${challenge.number} — ${challenge.paperTarget} squares",
            FlushState.Message.Kind.Unlock,
        )
    }

    /** Give the bowl back to ordinary play. */
    fun endDaily() {
        val s = _state.value
        if (s.daily == null) return

        var next = s
        fixtureBeforeDaily?.let { next = next.copy(fixture = it) }
        grimeBeforeDaily?.let {
            settings.putDouble(Key.GRIME, it)
            next = next.copy(grime = it)
        }
        fixtureBeforeDaily = null
        grimeBeforeDaily = null

        next = next.copy(
            paperPulled = 0,
            isPaperCut = false,
            isPaperTrailing = false,
            isClogged = false,
            plunges = 0,
        )
        // A finished attempt is kept so today stays finished; an abandoned one is not.
        if (next.daily?.isComplete == false) next = next.copy(daily = null)
        _state.value = next
    }

    private fun recordDaily(s: FlushState, blocked: Boolean, load: Int): FlushState {
        val run = s.daily ?: return s

        val mark = when {
            blocked -> DailyMark.Clogged
            s.isGolden -> DailyMark.Golden
            s.grade == FlushGrade.Perfect -> DailyMark.Perfect
            s.grade == FlushGrade.Good -> DailyMark.Good
            else -> DailyMark.Poor
        }

        var score = run.score
        if (!blocked) {
            // Hitting the day's paper target exactly is the whole puzzle.
            val base = Upkeep.points(load, s.isGolden).toDouble()
            val onTarget = load == challenge.paperTarget
            score += (base * (if (onTarget) DailyChallenge.TARGET_BONUS else 1.0)).roundToInt()
        }

        val updated = run.copy(marks = run.marks + mark, score = score)
        updated.save(settings)
        return s.copy(daily = updated)
    }

    // Upkeep

    /** Scrub it. Costs you nothing but the time, and a clean bowl flushes gold more often. */
    fun useWand() {
        val s = _state.value
        if (s.isFlushing || s.grime <= 0) return

        var next = s
        // Scrubbing spends water. During a daily the tank is not in play.
        if (!s.isDailyRunning) {
            if (s.flushesLeft < Upkeep.WAND_COST) {
                haptics.thud()
                show("No water left to scrub with.", FlushState.Message.Kind.Busy)
                return
            }
            val left = s.flushesLeft - Upkeep.WAND_COST
            next = next.copy(flushesLeft = left)
            if (left == 0) {
                next = next.copy(isRunOver = true)
                if (next.runScore > next.bestRun) {
                    next = next.copy(bestRun = next.runScore)
                    settings.putInt(Key.BEST_RUN, next.bestRun)
                }
            }
        }

        val wasFilthy = s.grime >= Upkeep.GRIMY_ABOVE
        settings.putDouble(Key.GRIME, 0.0)
        _state.value = next.copy(grime = 0.0)
        haptics.tick()
        show(
            if (wasFilthy) "Spotless. That was overdue." else "Spotless.",
            FlushState.Message.Kind.Unlock,
        )
    }

    // The roll

    /** Draw the sheet down. Called continuously while a finger drags it. */
    fun pullPaper(squares: Int) {
        val s = _state.value
        if (s.isFlushing || s.isClogged || s.isPaperCut) return
        val wanted = squares.coerceIn(0, Upkeep.PAPER_RANGE.last)
        if (wanted == s.paperPulled) return

        // One roll in a hundred is not paper. Decided once, the first time this sheet
        // is drawn, so pulling it back and forth cannot fish for it.
        var cash = false
        if (!cashRolled && wanted > 0) {
            cashRolled = true
            cash = random.nextInt(Upkeep.CASH_ODDS) == 0
        }

        _state.value = s.copy(paperPulled = wanted, isCashRoll = s.isCashRoll || cash)
        if (cash) {
            haptics.flush(golden = true, scale = 0.6)
            show("Hold on. That's not paper.", FlushState.Message.Kind.Golden)
        }
        haptics.tick()
    }

    /** Put a plain roll back on the wall. */
    private fun resetRoll(s: FlushState): FlushState {
        cashRolled = false
        return s.copy(paperPulled = 0, isPaperCut = false, isCashRoll = false)
    }

    /** Tear it off. Until this happens the sheet is still attached to the roll. */
    fun cutPaper() {
        val s = _state.value
        if (s.paperPulled <= 0) return

        // Cutting a sheet that a flush already dragged in is the first step out of
        // the blockage, not a normal tear.
        if (s.isPaperTrailing) {
            _state.update { it.copy(isPaperTrailing = false, paperPulled = 0, isPaperCut = false) }
            haptics.thud()
            show("Cut free. Now plunge it.", FlushState.Message.Kind.Busy)
            return
        }

        if (s.isPaperCut) return
        _state.update { it.copy(isPaperCut = true) }
        haptics.tick()
    }

    /** One pump. Five clears it. */
    fun plunge() {
        val s = _state.value
        if (!s.isClogged) return
        if (s.isPaperTrailing) {
            haptics.thud()
            show("It's still attached. Cut it.", FlushState.Message.Kind.Busy)
            return
        }

        val plunges = s.plunges + 1
        haptics.thud()

        if (plunges < Upkeep.PLUNGES_TO_CLEAR) {
            _state.update { it.copy(plunges = plunges) }
            show("${Upkeep.PLUNGES_TO_CLEAR - plunges} more", FlushState.Message.Kind.Busy)
            return
        }

        // Clearing a blockage churns the filth up rather than removing it. A whole
        // roll going down leaves the bowl in a state the wand is the only answer to.
        val grime = if (wasRunaway) {
            maxOf(s.grime, Upkeep.FILTHY_ABOVE + 0.02)
        } else {
            minOf(s.grime + 0.08, 1.0)
        }
        wasRunaway = false
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

        // A sheet still attached to the roll is not a quantity, it is an accident.
        // Money is the exception: it goes down however you feed it in, because a
        // one-in-a-hundred payout that punishes you is not a payout.
        val runaway = s.paperPulled > 0 && !s.isPaperCut && !s.isCashRoll
        val load = s.loadedPaper

        // Every flush leaves a little behind, and paper leaves more.
        val grime = (s.grime + Upkeep.GRIME_PER_FLUSH * (1 + load * 0.25)).coerceIn(0.0, 1.0)
        settings.putDouble(Key.GRIME, grime)

        // Settle whether it went down before recording, so the daily can mark a
        // blocked flush as blocked. A runaway roll is not a roll of the dice.
        val odds = Upkeep.clogChance(load, grime, s.grade, s.fixture.tolerance)
        val blocked = !s.isCashRoll && (runaway || random.nextDouble() < odds)

        var next = s.copy(
            flushStartMillis = null,
            totalFlushes = total,
            goldenFlushes = goldenTotal,
            grime = grime,
            celebrationStartMillis = if (s.isGolden) now else s.celebrationStartMillis,
        )

        val wasDaily = s.isDailyRunning
        if (wasDaily) {
            // The cost line belongs to the tank; a daily deducts nothing.
            next = recordDaily(next, blocked, load).copy(lastClogCost = 0)
        } else {
            next = spendFromTank(next, blocked, load, s.isCashRoll)
            val standings = s.standings.record(
                golden = s.isGolden,
                streak = s.streak,
                points = if (runaway) 0 else Upkeep.points(load, s.isGolden),
                atMillis = now,
                zone = zone,
            )
            standings.save(settings)
            next = next.copy(standings = standings)
        }
        val dailyFinished = wasDaily && next.daily?.isComplete == true

        if (blocked) {
            wasRunaway = runaway
            // An ordinary block still swallowed the sheet, so the roll starts over.
            // Only a runaway is still attached, and that is what has to be cut free.
            if (!runaway) next = resetRoll(next)
            next = next.copy(isClogged = true, isPaperTrailing = runaway, plunges = 0)
            _state.value = next
            if (s.isGolden) startCelebrationTimer()

            // The streak deliberately survives. It is earned pull by pull at the
            // handle, and a block is partly the dice — taking fifteen perfect pulls
            // away for one unlucky flush read as a punishment for nothing.
            haptics.thud()
            val cost = if (next.lastClogCost > 0) " −${fmt(next.lastClogCost)}" else ""
            show(
                (if (runaway) "The whole roll went in." else "Clogged.") + cost,
                FlushState.Message.Kind.Busy,
            )
        } else {
            // A tidy flush takes the sheet with it and leaves the roll ready again.
            val wasCash = s.isCashRoll
            next = resetRoll(next)
            if (wasCash) next = next.copy(isCashPayout = true, celebrationStartMillis = now)
            _state.value = next
            if (s.isGolden || wasCash) startCelebrationTimer()

            val earned = Fixture.all.firstOrNull { it.unlockAt in (before + 1)..total }
            val milestone = Quips.milestone(total)

            when {
                wasCash ->
                    show(quips.cashLine(), FlushState.Message.Kind.Golden)
                // Earning a new toilet outranks anything else the app had to say. The
                // gold still happens on screen, it just does not get the line.
                earned != null ->
                    show("Unlocked — ${earned.name}", FlushState.Message.Kind.Unlock)
                s.isGolden ->
                    show(quips.goldenLine(), FlushState.Message.Kind.Golden)
                // Flushing nothing but water is its own kind of achievement.
                load == 0 ->
                    show(quips.unwipedLine(), FlushState.Message.Kind.Busy)
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

        if (dailyFinished) {
            // The day's result outranks whatever the flush itself had to say, and the
            // bowl goes back to ordinary play rather than staying refused until the
            // app is next launched.
            show(
                "Daily done — ${fmt(next.daily?.score ?: 0)} points",
                FlushState.Message.Kind.Milestone,
            )
            endDaily()
        }
    }

    private fun startCelebrationTimer() {
        celebrationJob?.cancel()
        celebrationJob = scope.launch {
            // Longer than the slowest flake takes to fall (3.75s), so the gold lands
            // rather than blinking out mid-air.
            delay(CELEBRATION_MILLIS)
            _state.update { it.copy(celebrationStartMillis = null, isCashPayout = false) }
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

    private fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    private object Key {
        const val TOTAL = "totalFlushes"
        const val GOLDEN = "goldenFlushes"
        const val FIXTURE = "equippedFixture"
        const val BEST_STREAK = "bestStreak"
        const val BEST_RUN = "bestRun"
        const val GRIME = "grime"
    }

    private companion object {
        const val MESSAGE_MILLIS = 2_900L
        const val CELEBRATION_MILLIS = 3_800L

        /** What a saved game reads back as. */
        fun restore(settings: Settings, challenge: DailyChallenge): FlushState {
            val total = settings.getInt(Key.TOTAL)

            // Fall back to the standard toilet if the saved one is unknown, or if the
            // tally was wiped and it is no longer earned.
            val saved = Fixture.withId(settings.getString(Key.FIXTURE) ?: Fixture.Standard.id)

            return FlushState(
                challenge = challenge,
                totalFlushes = total.coerceAtLeast(0),
                goldenFlushes = settings.getInt(Key.GOLDEN).coerceAtLeast(0),
                bestStreak = settings.getInt(Key.BEST_STREAK).coerceAtLeast(0),
                bestRun = settings.getInt(Key.BEST_RUN).coerceAtLeast(0),
                // Clamped: a save can be edited on a rooted device, and loading is the
                // boundary where that gets caught rather than at every use.
                grime = settings.getDouble(Key.GRIME).coerceIn(0.0, 1.0),
                standings = Standings.load(settings),
                daily = DailyResult.load(settings, challenge.stamp),
                fixture = if (total >= saved.unlockAt) saved else Fixture.Standard,
            )
        }
    }
}
