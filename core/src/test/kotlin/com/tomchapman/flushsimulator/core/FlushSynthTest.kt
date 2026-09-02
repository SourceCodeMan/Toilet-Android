package com.tomchapman.flushsimulator.core

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The noise, which nobody can hear in a test.
 *
 * So these check the things that would still be wrong if it sounded like anything at
 * all: that a take is the right length, never spits, repeats exactly for a given seed,
 * and puts its energy where the flush does — quiet at the ends, loud through the
 * middle, with the tank hissing after the bowl has drained.
 */
class FlushSynthTest {

    private val rate = FlushSynth.SAMPLE_RATE

    /** Loudness between two moments, in seconds. */
    private fun rms(samples: FloatArray, from: Double, to: Double): Double {
        val a = (from * rate).toInt().coerceIn(0, samples.size)
        val b = (to * rate).toInt().coerceIn(a, samples.size)
        if (b <= a) return 0.0
        var sum = 0.0
        for (i in a until b) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (b - a))
    }

    @Test
    fun `a take runs as long as its fixture`() {
        for (fixture in Fixture.all) {
            val expected = (4.35 * fixture.profile.timeScale * rate).toInt()
            val take = FlushSynth.render(fixture.profile, 11, golden = false)
            assertEquals(expected, take.size, fixture.name)
        }
    }

    @Test
    fun `a brief fixture gets a brief noise`() {
        val chrome = FlushSynth.render(Fixture.Chrome.profile, 11, false).size
        val victorian = FlushSynth.render(Fixture.Victorian.profile, 11, false).size
        assertTrue(chrome < victorian, "chrome $chrome should be shorter than victorian $victorian")
    }

    @Test
    fun `nothing ever spits`() {
        // tanh into 0.55 is the soft clip; anything past it is a bug in the chain.
        for (fixture in Fixture.all) {
            for (golden in listOf(false, true)) {
                val take = FlushSynth.render(fixture.profile, 11, golden)
                val loudest = take.maxOf { abs(it) }
                assertTrue(loudest <= 0.5501f, "${fixture.name} golden=$golden peaked at $loudest")
                assertTrue(loudest > 0.05f, "${fixture.name} golden=$golden is basically silent")
            }
        }
    }

    @Test
    fun `the same seed gives the same flush every launch`() {
        val once = FlushSynth.render(FlushProfile.Standard, 4_242, false)
        val twice = FlushSynth.render(FlushProfile.Standard, 4_242, false)
        assertTrue(once contentEquals twice)
    }

    @Test
    fun `the three takes are actually three takes`() {
        val takes = FlushSynth.ORDINARY_SEEDS.map { FlushSynth.render(FlushProfile.Standard, it, false) }
        for (i in takes.indices) {
            for (j in i + 1 until takes.size) {
                assertTrue(!(takes[i] contentEquals takes[j]), "seeds $i and $j gave the same noise")
            }
        }
    }

    @Test
    fun `the flush is quiet before it starts and after it settles`() {
        val p = FlushProfile.Standard
        val take = FlushSynth.render(p, 11, false)

        // The clunk is at the very front, then the roar climbs.
        val middle = rms(take, 1.0, 1.8)
        val afterEverything = rms(take, 4.3, 4.35)
        assertTrue(middle > 0.05, "the middle of the flush should be loud, got $middle")
        assertTrue(afterEverything < middle / 3, "it should have settled, got $afterEverything vs $middle")
    }

    @Test
    fun `the tank hisses after the bowl has drained`() {
        val take = FlushSynth.render(FlushProfile.Standard, 11, false)
        // The roar is done by 2.85s; the hiss runs to 4.15s.
        val gapIfNoHiss = rms(take, 3.4, 3.9)
        assertTrue(gapIfNoHiss > 0.01, "nothing is refilling the tank, got $gapIfNoHiss")
    }

    @Test
    fun `a golden flush carries the fanfare`() {
        val p = FlushProfile.Standard
        val ordinary = FlushSynth.render(p, FlushSynth.GOLDEN_SEED, golden = false)
        val golden = FlushSynth.render(p, FlushSynth.GOLDEN_SEED, golden = true)

        // Same seed, so the only difference can be the notes, which start at 2.15s.
        assertTrue(rms(ordinary, 0.0, 2.0) == rms(golden, 0.0, 2.0), "the front should be identical")
        assertTrue(
            rms(golden, 2.2, 3.0) > rms(ordinary, 2.2, 3.0),
            "the fanfare should be audible over the flush",
        )
    }

    @Test
    fun `every fixture sounds like itself`() {
        // Same seed across fixtures: any two matching takes would mean the profile is
        // not reaching the synthesiser.
        val takes = Fixture.all.map { it.id to FlushSynth.render(it.profile, 11, false) }
        for (i in takes.indices) {
            for (j in i + 1 until takes.size) {
                val (a, x) = takes[i]
                val (b, y) = takes[j]
                val shared = minOf(x.size, y.size)
                assertTrue(
                    !(x.copyOf(shared) contentEquals y.copyOf(shared)),
                    "$a and $b render identically",
                )
            }
        }
    }
}
