package com.tomchapman.flushsimulator.core

/**
 * The character of a single flush, as numbers.
 *
 * [FlushTimeline] and the audio synthesiser were both written against hard-coded
 * constants, which is exactly right for an app with one toilet in it. A profile
 * lifts those constants into a value so a fixture can own its own surge, its own
 * swirl and its own voice, without either of those learning what a fixture is.
 *
 * [Standard] reproduces the original numbers exactly, so swapping the constants for
 * a profile changes nothing until something hands over a different one.
 */
data class FlushProfile(

    // Shape of the flush

    /** How long a flush takes, start to settled, in seconds. */
    val duration: Double,

    /** Water level in the bowl at rest. 0 = empty, 1 = brimming. */
    val restingLevel: Double,

    /** How high the surge climbs before the bowl lets go. */
    val surgePeak: Double,

    /** Degrees per second at full churn. */
    val spinPeak: Double,

    /** Sideways travel of the whole fixture, in points. */
    val rumbleScale: Double,

    /** Chop on the water's surface. */
    val chop: Double,

    // Voice

    /** The handle bottoming out. */
    val clunkFrequency: Double,

    /** The roar sweeps between these as the bowl empties. */
    val roarFrom: Double,
    val roarTo: Double,

    /** The low body underneath the roar. */
    val bodyFrequency: Double,

    /** The uneven glugging: centre frequency, and how far it wanders. */
    val gurgleCentre: Double,
    val gurgleSwing: Double,

    /** The tank refilling, rising in pitch as it fills. */
    val hissFrom: Double,
    val hissTo: Double,

    /** The float valve shutting off at the end. */
    val valveFrequency: Double,
) {

    /**
     * How this flush's timing compares with the original toilet's.
     *
     * Every hard-coded moment in [FlushTimeline], the audio and the haptics was tuned
     * against [Standard], so those scale their constants by this rather than each
     * learning what a fixture is. A 2.6-second fixture gets the same shape as the
     * 3.6-second one it was tuned from, and [Standard] — where this is exactly 1 —
     * is left untouched.
     */
    val timeScale: Double get() = maxOf(duration, 0.1) / Standard.duration

    companion object {
        /**
         * The original toilet, to the number. Changing anything here changes the app
         * that already shipped, so don't — add a new profile instead.
         */
        val Standard = FlushProfile(
            duration = 3.6,
            restingLevel = 0.52,
            surgePeak = 0.95,
            spinPeak = 1_400.0,
            rumbleScale = 1.7,
            chop = 0.014,
            clunkFrequency = 94.0,
            roarFrom = 1_250.0,
            roarTo = 370.0,
            bodyFrequency = 165.0,
            gurgleCentre = 620.0,
            gurgleSwing = 250.0,
            hissFrom = 2_150.0,
            hissTo = 3_100.0,
            valveFrequency = 118.0,
        )
    }
}
