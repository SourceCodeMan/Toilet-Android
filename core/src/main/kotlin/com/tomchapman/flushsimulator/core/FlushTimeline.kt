package com.tomchapman.flushsimulator.core

import kotlin.math.pow
import kotlin.math.sin

/**
 * One flush, described as pure functions of elapsed time.
 *
 * The drawing reads these functions every frame, and the engine schedules the sound,
 * the haptics and the payoff line against the same numbers, so the picture and the
 * noise can't drift apart.
 *
 * The numbers that give a flush its character live in [FlushProfile], so a different
 * fixture can surge higher or swirl longer without this file changing. The shape of
 * the curves is still here; only the magnitudes travel.
 */
object FlushTimeline {

    /**
     * How far the handle is pushed down, 0 = resting, 1 = bottomed out.
     *
     * The handle is the same lever on every fixture, so this one takes no profile.
     * It is also over inside 0.6s, which is well inside the shortest flush there is.
     */
    fun handlePush(t: Double): Double =
        // Slam down, hang there for a beat, then let the spring take it back.
        segment(t, 0.0, 0.07) - segment(t, 0.24, 0.58)

    /**
     * What is left in the bowl at the bottom of the drain, before the tank refills.
     *
     * The floor of the basin rather than anything a fixture chooses, so it stays a
     * constant — but the drain and the refill are both measured against it rather
     * than against a hard-coded depth, which is what makes a flush finish at exactly
     * the level it started from whatever the fixture's surge and resting level are.
     */
    private const val DRAINED = 0.05

    fun level(t: Double, p: FlushProfile): Double {
        val s = p.timeScale
        var level = p.restingLevel
        level += (p.surgePeak - p.restingLevel) * segment(t, 0.10 * s, 0.55 * s)  // the surge that always looks like an overflow
        level -= (p.surgePeak - DRAINED) * segment(t, 0.55 * s, 1.35 * s)         // ...and then it all goes
        level += (p.restingLevel - DRAINED) * segment(t, 1.60 * s, 3.30 * s)      // tank refills
        level += p.chop * sin(t * 17.0) * turbulence(t, p)                        // chop on the surface
        return level.coerceIn(0.0, 1.0)
    }

    /**
     * Total rotation of the water since the flush began, in degrees.
     *
     * Piecewise so the velocity is continuous: it winds up over `windUp`, then eases
     * off to a stop. Integrated by hand rather than sampled, because the view can be
     * asked for any `t` at any time.
     */
    fun spin(t: Double, p: FlushProfile): Double {
        val peak = p.spinPeak   // degrees per second at full churn
        val windUp = 0.40 * p.timeScale
        val stop = 3.20 * p.timeScale
        if (t <= 0) return 0.0
        if (t < windUp) return peak * t * t / (2 * windUp)
        val windUpTotal = peak * windUp / 2
        if (t >= stop) return windUpTotal + peak * (stop - windUp) / 3
        val u = (t - windUp) / (stop - windUp)
        return windUpTotal + peak * (stop - windUp) * (1 - (1 - u).pow(3.0)) / 3
    }

    /**
     * How hard the water is churning, 0..1. Drives foam, bubbles and the vortex.
     *
     * Scaled with the flush like everything else, so a short fixture — or a weak
     * pull, which shortens one — settles rather than being cut off mid-churn.
     */
    fun turbulence(t: Double, p: FlushProfile): Double {
        val s = p.timeScale
        return (segment(t, 0.05 * s, 0.35 * s) - segment(t, 1.90 * s, 3.10 * s)).coerceIn(0.0, 1.0)
    }

    /** Sideways shake of the whole fixture, in points. */
    fun rumble(t: Double, p: FlushProfile): Double =
        turbulence(t, p) * (sin(t * 41.3) * 0.65 + sin(t * 67.7) * 0.35) * p.rumbleScale

    // Easing

    private fun segment(t: Double, start: Double, end: Double): Double {
        if (end <= start) return if (t < start) 0.0 else 1.0
        val x = ((t - start) / (end - start)).coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }
}
