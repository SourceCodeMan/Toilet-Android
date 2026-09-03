package com.tomchapman.flushsimulator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The buzz, which nobody can feel in a test either.
 *
 * CoreHaptics played a curve; Android plays rungs. These check the rungs still carry
 * the shape the curve had — a hard knock at the front, a swell that peaks early and
 * fades out with the water, and four taps under the fanfare on a golden one.
 */
class HapticPatternTest {

    private fun total(steps: List<HapticStep>) = steps.sumOf { it.millis }

    /** The strength at a given moment, in millis from the start of the pattern. */
    private fun at(steps: List<HapticStep>, millis: Long): Int {
        var clock = 0L
        for (step in steps) {
            clock += step.millis
            if (millis < clock) return step.amplitude
        }
        return steps.last().amplitude
    }

    @Test
    fun `the swell keeps the curve's own control points`() {
        assertEquals(0.2, HapticPattern.swell(0.0, 1.0), 1e-9)
        assertEquals(1.0, HapticPattern.swell(0.35, 1.0), 1e-9)
        assertEquals(0.7, HapticPattern.swell(1.5, 1.0), 1e-9)
        assertEquals(0.05, HapticPattern.swell(2.9, 1.0), 1e-9)
        // Linear between them, as CoreHaptics interpolates.
        assertEquals(0.6, HapticPattern.swell(0.175, 1.0), 1e-9)
        // And it holds at the ends rather than running off.
        assertEquals(0.2, HapticPattern.swell(-1.0, 1.0), 1e-9)
        assertEquals(0.05, HapticPattern.swell(99.0, 1.0), 1e-9)
    }

    @Test
    fun `the swell stretches with the fixture`() {
        assertEquals(1.0, HapticPattern.swell(0.35 * 2, 2.0), 1e-9)
        assertEquals(0.7, HapticPattern.swell(1.5 * 2, 2.0), 1e-9)
    }

    @Test
    fun `a flush opens with the lever bottoming out`() {
        val steps = HapticPattern.flush(golden = false, scale = 1.0)
        assertEquals(HapticPattern.MAX_AMPLITUDE, steps.first().amplitude)
        assertTrue(steps.first().millis <= 20, "the knock should be a knock, not a buzz")
    }

    @Test
    fun `a flush runs about as long as the water does`() {
        for (fixture in Fixture.all) {
            val scale = fixture.profile.timeScale
            val steps = HapticPattern.flush(false, scale)
            val expected = (100 * scale + 2_900 * scale).toLong()
            // Rungs are whole steps, so the tail can overrun by less than one.
            assertTrue(
                total(steps) in expected..(expected + HapticPattern.STEP_MILLIS),
                "${fixture.name}: ${total(steps)}ms against about ${expected}ms",
            )
        }
    }

    @Test
    fun `the rumble swells and then fades with the water`() {
        val steps = HapticPattern.flush(false, 1.0)
        val opening = at(steps, 120)      // just after the rumble starts
        val peak = at(steps, 100 + 350)   // where the curve tops out
        val late = at(steps, 100 + 1_500)
        val end = at(steps, 100 + 2_850)

        assertTrue(peak > opening, "should swell: $opening -> $peak")
        assertTrue(late < peak, "should ease off: $peak -> $late")
        assertTrue(end < late, "should fade out: $late -> $end")
    }

    @Test
    fun `a golden flush taps four times under the fanfare`() {
        val plain = HapticPattern.flush(golden = false, scale = 1.0)
        val golden = HapticPattern.flush(golden = true, scale = 1.0)

        assertEquals(plain.size, golden.size, "the taps replace rungs rather than adding any")

        val differing = plain.indices.filter { plain[it].amplitude != golden[it].amplitude }
        assertEquals(4, differing.size, "expected four taps, got ${differing.size}")
        for (i in differing) {
            assertTrue(golden[i].amplitude > plain[i].amplitude, "a tap should be felt over the rumble")
        }
    }

    @Test
    fun `the taps land where the notes do`() {
        val golden = HapticPattern.flush(golden = true, scale = 1.0)
        val plain = HapticPattern.flush(golden = false, scale = 1.0)
        val differing = plain.indices.filter { plain[it].amplitude != golden[it].amplitude }

        // The fanfare starts at 2.15s and steps every 0.13s.
        var clock = 0L
        val starts = golden.map { clock.also { _ -> clock += it.millis } }
        val tapTimes = differing.map { starts[it] }
        tapTimes.forEachIndexed { step, millis ->
            val expected = (2_150 + step * 130).toLong()
            assertTrue(
                kotlin.math.abs(millis - expected) <= HapticPattern.STEP_MILLIS,
                "tap $step landed at ${millis}ms, expected about ${expected}ms",
            )
        }
    }

    // Hardware with no amplitude control

    @Test
    fun `pulsing only ever asks for on or off`() {
        val pulsed = HapticPattern.pulsed(HapticPattern.flush(golden = false, scale = 1.0))
        assertTrue(pulsed.isNotEmpty())
        for (step in pulsed) {
            assertTrue(
                step.amplitude == 0 || step.amplitude == HapticPattern.MAX_AMPLITUDE,
                "a device with no amplitude control was asked for ${step.amplitude}",
            )
            assertTrue(step.millis > 0, "a slice with no length")
        }
    }

    @Test
    fun `pulsing keeps the pattern's length`() {
        for (fixture in Fixture.all) {
            val steps = HapticPattern.flush(false, fixture.profile.timeScale)
            val pulsed = HapticPattern.pulsed(steps)
            // The silent tail is trimmed, so it may end early — but never run long.
            assertTrue(
                total(pulsed) <= total(steps),
                "${fixture.name}: ${total(pulsed)}ms against ${total(steps)}ms",
            )
            assertTrue(total(pulsed) > total(steps) * 0.7, "${fixture.name} lost most of its buzz")
        }
    }

    /**
     * The point of the whole exercise: on hardware that cannot vary strength, the
     * loud part of the flush should still be more *on* than the quiet part.
     */
    @Test
    fun `pulsing turns the swell into a duty cycle`() {
        val pulsed = HapticPattern.pulsed(HapticPattern.flush(false, 1.0))

        fun onTimeBetween(from: Long, to: Long): Long {
            var clock = 0L
            var on = 0L
            for (step in pulsed) {
                val start = clock
                val end = clock + step.millis
                if (step.amplitude > 0) {
                    on += (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)
                }
                clock = end
            }
            return on
        }

        // Around the peak of the swell against the fading tail, same window length.
        val loud = onTimeBetween(400, 900)
        val quiet = onTimeBetween(2_300, 2_800)
        assertTrue(loud > quiet, "the swell did not survive: loud=${loud}ms quiet=${quiet}ms")
        // Not solid, and should not be: the continuous event's own intensity is 0.75,
        // so three quarters on is the ceiling the curve is played through.
        assertTrue(loud > 300, "the middle should be mostly on, got ${loud}ms of 500")
        assertTrue(loud < 500, "the middle should not be pinned on, got ${loud}ms of 500")
    }

    @Test
    fun `the opening knock is left whole`() {
        val pulsed = HapticPattern.pulsed(HapticPattern.flush(false, 1.0))
        // Shorter than a cycle, so chopping it would only make it disappear.
        assertEquals(HapticPattern.MAX_AMPLITUDE, pulsed.first().amplitude)
        assertTrue(pulsed.first().millis <= HapticPattern.PWM_PERIOD_MILLIS)
    }

    @Test
    fun `a tick survives pulsing intact`() {
        val tick = HapticPattern.pulsed(HapticPattern.tick())
        assertEquals(1, tick.size)
        assertEquals(HapticPattern.MAX_AMPLITUDE, tick.single().amplitude)
    }

    @Test
    fun `a pulsed pattern stays short enough for a vibrator to accept`() {
        // Chopping every rung into a duty cycle is the obvious way to produce a
        // waveform some HAL refuses. No limit is documented anywhere I can find, and
        // the longest fixture measures 261 entries and plays, so this guards against
        // an explosion rather than enforcing a known ceiling.
        for (fixture in Fixture.all) {
            for (golden in listOf(false, true)) {
                val pulsed = HapticPattern.pulsed(HapticPattern.flush(golden, fixture.profile.timeScale))
                assertTrue(
                    pulsed.size <= 400,
                    "${fixture.name} golden=$golden produced ${pulsed.size} entries",
                )
            }
        }
    }

    @Test
    fun `a tick is sharper than a thud, which is all Android can say`() {
        val tick = HapticPattern.tick().single()
        val thud = HapticPattern.thud().single()
        // Sharpness has no counterpart, so length carries it: shorter reads crisper.
        assertTrue(tick.millis < thud.millis)
        assertTrue(tick.amplitude < thud.amplitude)
    }

    @Test
    fun `every rung is something Android will accept`() {
        for (fixture in Fixture.all) {
            for (golden in listOf(false, true)) {
                for (step in HapticPattern.flush(golden, fixture.profile.timeScale)) {
                    assertTrue(step.millis > 0, "a rung with no length")
                    assertTrue(step.amplitude in 0..HapticPattern.MAX_AMPLITUDE, "amplitude ${step.amplitude}")
                }
            }
        }
    }
}
