package com.tomchapman.flushsimulator.core

/**
 * Everything on screen, in one value.
 *
 * The Swift spread this over some thirty `@Published` properties on an
 * `ObservableObject`. One immutable snapshot in a single `StateFlow` is the Compose
 * equivalent, and it makes a test a single assertion rather than thirty.
 */
data class FlushState(
    /** Today's puzzle. Derived from the date once, when the engine is built. */
    val challenge: DailyChallenge,

    /** When the current flush began, in epoch millis, or null if the bowl is at rest. */
    val flushStartMillis: Long? = null,
    val isGolden: Boolean = false,
    val totalFlushes: Int = 0,
    val goldenFlushes: Int = 0,
    val message: Message? = null,
    val celebrationStartMillis: Long? = null,

    /** Consecutive perfect pulls. Resets on a weak or overheld one. */
    val streak: Int = 0,

    /** The best run of perfect pulls so far. */
    val bestStreak: Int = 0,

    /** How the flush currently running was pulled. */
    val grade: FlushGrade = FlushGrade.Good,

    /** How filthy the bowl is, 0..1. */
    val grime: Double = 0.0,

    // The roll on the wall

    /** Squares hanging off the roll, pulled but not yet torn. */
    val paperPulled: Int = 0,

    /** True once the sheet has been torn off and is sitting ready. */
    val isPaperCut: Boolean = false,

    /** This sheet came off the roll as hundred dollar bills. One in a hundred. */
    val isCashRoll: Boolean = false,

    /** True while the payout card is on screen. */
    val isCashPayout: Boolean = false,

    /**
     * The sheet was never torn, so the flush dragged the roll in with it. Nothing
     * clears until it is cut free.
     */
    val isPaperTrailing: Boolean = false,

    // The blockage

    /** True while the bowl is blocked. Nothing flushes until it is cleared. */
    val isClogged: Boolean = false,

    /** Pumps landed on the current blockage. */
    val plunges: Int = 0,

    /** Day-by-day record, for the leaderboard. */
    val standings: Standings = Standings(),

    // The tank

    /** Flushes left before this tank runs dry. */
    val flushesLeft: Int = Upkeep.RUN_LENGTH,

    /** What this tank has been worth so far. */
    val runScore: Int = 0,

    /** The best tank yet. */
    val bestRun: Int = 0,

    /** True once the tank is dry and the score is final. */
    val isRunOver: Boolean = false,

    /** What the last blockage actually took off the score, for the message. */
    val lastClogCost: Int = 0,

    /**
     * Bumped every time something asks for water that is not there.
     *
     * The summary used to be the only route to a new tank, so dismissing it left the
     * game with no legal move: dry, and no way to say so. This lets asking for a flush
     * bring the summary back instead of just refusing.
     */
    val dryTankAsks: Int = 0,

    // The daily

    /** Your attempt at today's puzzle, or null if you have not started it. */
    val daily: DailyResult? = null,

    /**
     * The fixture currently installed. Its profile drives the flush and its palette
     * dresses the app.
     */
    val fixture: Fixture = Fixture.Standard,
) {
    val isFlushing: Boolean get() = flushStartMillis != null

    /** True while the whole app should go gold. */
    val showsGold: Boolean get() = isGolden && (isFlushing || celebrationStartMillis != null)

    /** The bowl is bad enough that flushing it costs you the run. */
    val isFilthy: Boolean get() = grime >= Upkeep.FILTHY_ABOVE

    /** How this fixture flushes and sounds. */
    val profile: FlushProfile get() = fixture.profile

    /**
     * The profile actually driving the flush on screen: the fixture's, scaled by how
     * well the handle was pulled.
     */
    val activeProfile: FlushProfile get() = grade.applyTo(fixture.profile)

    /**
     * What actually goes down on this flush.
     *
     * An uncut sheet does not go down as a tidy stack: the bowl keeps pulling for the
     * whole flush, so it counts as far more than was ever hanging there. Pulling
     * nothing at all is not the same thing as leaving it attached, though — an empty
     * roll is uncut by definition, and that must not read as a runaway.
     */
    val loadedPaper: Int
        get() = when {
            paperPulled <= 0 -> 0
            isPaperCut -> paperPulled
            else -> Upkeep.RUNAWAY_PAPER
        }

    val isDailyRunning: Boolean get() = daily?.isComplete == false
    val isDailyDone: Boolean get() = daily?.isComplete == true

    data class Message(
        val id: Long,
        val text: String,
        val kind: Kind,
    ) {
        enum class Kind { Quip, Milestone, Golden, Busy, Unlock }
    }
}
