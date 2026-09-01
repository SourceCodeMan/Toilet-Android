package com.tomchapman.flushsimulator.core

/**
 * Everything on screen, in one value.
 *
 * The Swift spread this over a dozen `@Published` properties on an `ObservableObject`.
 * One immutable snapshot in a single `StateFlow` is the Compose equivalent, and it
 * makes a test a single assertion rather than a dozen.
 */
data class FlushState(
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

    /** Squares going in on the next flush. */
    val paper: Int = Upkeep.DEFAULT_PAPER,

    /** True while the bowl is blocked. Nothing flushes until it is cleared. */
    val isClogged: Boolean = false,

    /** Pumps landed on the current blockage. */
    val plunges: Int = 0,

    /** Day-by-day record, for the leaderboard. */
    val standings: Standings = Standings(),

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

    data class Message(
        val id: Long,
        val text: String,
        val kind: Kind,
    ) {
        enum class Kind { Quip, Milestone, Golden, Busy, Unlock }
    }
}
