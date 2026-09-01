package com.tomchapman.flushsimulator.core

/**
 * How well the handle was pulled.
 *
 * Real cisterns want the lever held down for a moment — let go early and you get a
 * half flush, lean on it and you are just wasting water. That is the whole skill:
 * hold, and let go inside the window.
 */
enum class FlushGrade {

    /** Released before the cistern really got going. */
    Weak,

    /** Held about right. */
    Good,

    /** Released inside the window. */
    Perfect,

    /** Leaned on it. Counts, but nobody is impressed. */
    Overheld;

    // Consequences

    /** Does this keep a streak alive? */
    val keepsStreak: Boolean get() = this == Perfect

    /** Does this break one that is already running? */
    val breaksStreak: Boolean get() = this == Weak || this == Overheld

    val label: String
        get() = when (this) {
            Weak -> "Half flush"
            Good -> "Good flush"
            Perfect -> "Perfect flush"
            Overheld -> "Held too long"
        }

    /**
     * The flush this grade earns you.
     *
     * A weak pull barely disturbs the bowl; a perfect one surges higher and spins
     * harder than the fixture's own numbers. The fixture still sets the character —
     * this only scales it.
     */
    fun applyTo(p: FlushProfile): FlushProfile = when (this) {
        Weak -> p.copy(
            surgePeak = p.restingLevel + (p.surgePeak - p.restingLevel) * 0.35,
            spinPeak = p.spinPeak * 0.45,
            duration = p.duration * 0.72,
            rumbleScale = p.rumbleScale * 0.5,
        )
        Good -> p
        Perfect -> p.copy(
            surgePeak = minOf(p.surgePeak * 1.06, 0.995),
            spinPeak = p.spinPeak * 1.22,
            rumbleScale = p.rumbleScale * 1.15,
        )
        // All the noise, none of the grace.
        Overheld -> p.copy(
            spinPeak = p.spinPeak * 0.85,
            duration = p.duration * 1.15,
            rumbleScale = p.rumbleScale * 1.3,
        )
    }

    companion object {
        // The window

        /** Below this, the cistern has barely opened. */
        const val WEAK_UNTIL = 0.34

        /** The window you are aiming for, in seconds of hold. */
        const val PERFECT_FROM = 0.55
        const val PERFECT_UNTIL = 0.88

        /** Past here you are just holding a lever. */
        const val OVERHELD_FROM = 1.35

        /** The full travel drawn on the meter. */
        const val METER_SPAN = 1.5

        fun forHold(seconds: Double): FlushGrade = when {
            seconds < WEAK_UNTIL -> Weak
            seconds >= PERFECT_FROM && seconds < PERFECT_UNTIL -> Perfect
            seconds >= OVERHELD_FROM -> Overheld
            else -> Good
        }
    }
}
