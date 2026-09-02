package com.tomchapman.flushsimulator.core

import kotlin.math.roundToInt

/** One rung of a vibration: hold this strength for this long. */
data class HapticStep(val millis: Long, val amplitude: Int)

/**
 * The flush you can feel, as something Android can actually play.
 *
 * CoreHaptics takes events with an intensity *and* a sharpness, laid over a parameter
 * curve that reshapes them as they run. Android takes a list of amplitudes and how
 * long to hold each one. So the curve is sampled into rungs here rather than handed
 * over whole, and sharpness has nowhere to go at all — the crisp tick and the dull
 * thud are separated by their length instead, which is the only lever left.
 *
 * Pure, and in `core`, so the shape of a buzz nobody can feel in a test is still
 * something a test can check.
 */
object HapticPattern {

    /** How finely the swell is sampled. Below about this, the rungs start to buzz. */
    const val STEP_MILLIS = 40L

    /** Android's amplitude scale. */
    const val MAX_AMPLITUDE = 255

    /** The lever catching, the instant you press it. Sharp, so: short. */
    fun tick(): List<HapticStep> = listOf(HapticStep(12, amplitude(0.42)))

    /** A short knock, for when you mash the handle mid-flush. */
    fun thud(): List<HapticStep> = listOf(HapticStep(16, amplitude(0.55)))

    /**
     * The whole flush: the lever bottoming out, then a long rumble that swells and
     * fades with the water.
     *
     * [scale] stretches the buzz with the fixture, the same way the noise and the
     * picture stretch.
     */
    fun flush(golden: Boolean, scale: Double): List<HapticStep> {
        val steps = mutableListOf<HapticStep>()

        // The lever bottoming out: the one thing that is genuinely a transient.
        steps += HapticStep(TRANSIENT_MILLIS, MAX_AMPLITUDE)

        val startMillis = (100 * scale).toLong()
        val gap = startMillis - TRANSIENT_MILLIS
        if (gap > 0) steps += HapticStep(gap, 0)

        // The water itself, sampled off the swell.
        val runMillis = (2_900 * scale).toLong()
        val rungs = ((runMillis + STEP_MILLIS - 1) / STEP_MILLIS).toInt()
        for (rung in 0 until rungs) {
            val at = rung * STEP_MILLIS + STEP_MILLIS / 2      // the middle of the rung
            steps += HapticStep(STEP_MILLIS, amplitude(CONTINUOUS * swell(at / 1_000.0, scale)))
        }

        if (!golden) return steps

        // Four taps under the fanfare. A waveform cannot lay one buzz over another, so
        // the rung each tap lands in is turned up to be the tap.
        val offset = steps.size - rungs
        for (step in 0 until 4) {
            val at = (2_150 * scale + step * 130).toLong() - startMillis
            val rung = (at / STEP_MILLIS).toInt()
            if (rung in 0 until rungs) {
                steps[offset + rung] = HapticStep(STEP_MILLIS, amplitude(GOLDEN_TAP))
            }
        }
        return steps
    }

    /**
     * The intensity curve the rumble is played through, 0..1.
     *
     * CoreHaptics interpolates linearly between its control points, so this does too.
     * [seconds] is measured from where the rumble starts, not from the pull.
     */
    fun swell(seconds: Double, scale: Double): Double {
        val points = listOf(
            0.0 to 0.2,
            0.35 * scale to 1.0,
            1.5 * scale to 0.7,
            2.9 * scale to 0.05,
        )
        if (seconds <= points.first().first) return points.first().second
        if (seconds >= points.last().first) return points.last().second

        for (i in 0 until points.size - 1) {
            val (t0, v0) = points[i]
            val (t1, v1) = points[i + 1]
            if (seconds in t0..t1) {
                val span = t1 - t0
                if (span <= 0) return v1
                return v0 + (v1 - v0) * (seconds - t0) / span
            }
        }
        return points.last().second
    }

    private fun amplitude(intensity: Double): Int =
        (intensity.coerceIn(0.0, 1.0) * MAX_AMPLITUDE).roundToInt().coerceAtLeast(1)

    private const val TRANSIENT_MILLIS = 18L

    /** What the rumble is worth before the swell reshapes it. */
    private const val CONTINUOUS = 0.75

    private const val GOLDEN_TAP = 0.8
}
