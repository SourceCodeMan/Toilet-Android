package com.tomchapman.flushsimulator.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The flush is pure maths, so it can be pinned down exactly.
 *
 * These are the invariants the Swift's comments claim out loud — that a flush starts
 * and ends at rest, that the bowl surges before it drains, that the spin only ever
 * winds forward. If the port broke a constant, one of these goes red.
 */
class FlushTimelineTest {

    private val profiles = Fixture.all.map { it.name to it.profile }

    @Test
    fun `every fixture starts at its resting level`() {
        for ((name, p) in profiles) {
            assertEquals(p.restingLevel, FlushTimeline.level(0.0, p), 1e-9, name)
        }
    }

    @Test
    fun `every fixture settles back to where it started`() {
        // "the first and last frames land exactly on the resting state"
        for ((name, p) in profiles) {
            val settled = FlushTimeline.level(p.duration, p)
            assertTrue(
                abs(settled - p.restingLevel) < 0.01,
                "$name settled at $settled, resting is ${p.restingLevel}",
            )
        }
    }

    @Test
    fun `the bowl surges before it drains`() {
        for ((name, p) in profiles) {
            val s = p.timeScale
            val peak = FlushTimeline.level(0.55 * s, p)
            val bottom = FlushTimeline.level(1.35 * s, p)
            assertTrue(peak > p.restingLevel, "$name never surged: $peak")
            assertTrue(bottom < p.restingLevel, "$name never drained: $bottom")
            assertTrue(peak <= 1.0 && bottom >= 0.0, "$name left 0..1")
        }
    }

    @Test
    fun `level stays inside the bowl for the whole flush`() {
        for ((name, p) in profiles) {
            var t = 0.0
            while (t <= p.duration + 1.0) {
                val level = FlushTimeline.level(t, p)
                assertTrue(level in 0.0..1.0, "$name at t=$t gave $level")
                t += 1.0 / 120
            }
        }
    }

    @Test
    fun `spin only ever winds forward`() {
        for ((name, p) in profiles) {
            var previous = 0.0
            var t = 0.0
            while (t <= p.duration + 1.0) {
                val spin = FlushTimeline.spin(t, p)
                assertTrue(spin >= previous - 1e-9, "$name went backwards at t=$t")
                previous = spin
                t += 1.0 / 120
            }
        }
    }

    @Test
    fun `spin is continuous across the wind-up seam`() {
        // The piecewise join at windUp is the one place a transcription slip would
        // show as a visible jolt rather than a wrong number. Continuous means the gap
        // either side is only what the velocity itself carries across the sample —
        // spinPeak is degrees per second, and the wind-up ends at full speed — so the
        // bound has to be the velocity's, not an arbitrary epsilon.
        val eps = 1e-6
        for ((name, p) in profiles) {
            val windUp = 0.40 * p.timeScale
            val before = FlushTimeline.spin(windUp - eps, p)
            val after = FlushTimeline.spin(windUp + eps, p)
            val carried = p.spinPeak * 2 * eps
            assertTrue(
                abs(after - before) <= carried * 1.5,
                "$name jumps at the seam: $before -> $after (velocity allows $carried)",
            )
        }
    }

    @Test
    fun `the standard toilet swirls about four and a half times`() {
        // The README's claim, which is a real constraint on spinPeak and the curve.
        val total = FlushTimeline.spin(FlushProfile.Standard.duration, FlushProfile.Standard)
        assertTrue(total / 360.0 in 4.0..5.0, "swirled ${total / 360.0} times")
    }

    @Test
    fun `turbulence rises from nothing and returns to nothing`() {
        for ((name, p) in profiles) {
            assertEquals(0.0, FlushTimeline.turbulence(0.0, p), 1e-9, name)
            assertTrue(FlushTimeline.turbulence(1.0 * p.timeScale, p) > 0.9, "$name never churned")
            assertEquals(0.0, FlushTimeline.turbulence(3.2 * p.timeScale, p), 1e-9, "$name never settled")
        }
    }

    @Test
    fun `the handle slams and springs back`() {
        assertEquals(0.0, FlushTimeline.handlePush(0.0), 1e-9)
        assertEquals(1.0, FlushTimeline.handlePush(0.1), 1e-9)     // bottomed out
        assertEquals(1.0, FlushTimeline.handlePush(0.2), 1e-9)     // hanging there
        assertEquals(0.0, FlushTimeline.handlePush(0.6), 1e-9)     // spring took it back
        assertEquals(0.0, FlushTimeline.handlePush(3.0), 1e-9)
    }

    @Test
    fun `rumble is bounded by the fixture's own scale`() {
        for ((name, p) in profiles) {
            var t = 0.0
            while (t <= p.duration) {
                assertTrue(abs(FlushTimeline.rumble(t, p)) <= p.rumbleScale + 1e-9, "$name at t=$t")
                t += 1.0 / 120
            }
        }
    }

    @Test
    fun `the standard profile is left exactly as it was`() {
        // timeScale is the multiplier every other file leans on; the original toilet
        // has to come out at exactly 1 or every tuned constant shifts under it.
        assertEquals(1.0, FlushProfile.Standard.timeScale, 0.0)
    }
}
