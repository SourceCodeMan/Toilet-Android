package com.tomchapman.flushsimulator.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The flush noise, built from scratch.
 *
 * A recording would mean shipping a binary blob nobody can read, so the sound is
 * synthesised instead: a clunk off the handle, a roar of band-passed noise whose
 * centre frequency sweeps down as the bowl empties, a wobbling gurgle, and the long
 * hiss of the tank refilling behind it.
 *
 * This is pure arithmetic and lives in `core` for the same reason the rest of the
 * rules do — it can be rendered and checked on a plain JVM, and written to a wav
 * anyone can listen to, without an Android device anywhere in sight. The platform
 * only supplies somewhere to play the samples.
 */
object FlushSynth {

    const val SAMPLE_RATE = 44_100

    /** C, E, G, C. Hoisted out of the render loop, which runs 190,000 times a take. */
    private val FANFARE = doubleArrayOf(523.25, 659.25, 783.99, 1_046.50)

    /** The seeds behind the three takes, so the same noise never repeats back to back. */
    val ORDINARY_SEEDS = longArrayOf(11, 4_242, 90_210)
    const val GOLDEN_SEED = 777L

    /** How long a take runs, which is a little past the flush it belongs to. */
    fun seconds(profile: FlushProfile): Double = 4.35 * profile.timeScale

    /**
     * One take, as mono samples in -1..1.
     *
     * Every moment below was tuned against the standard toilet, so the whole take
     * stretches with this fixture's own duration and lands where its flush does. The
     * clunk is the exception: a knock is a knock on any cistern.
     */
    fun render(profile: FlushProfile, seed: Long, golden: Boolean): FloatArray {
        val s = profile.timeScale
        val frames = (seconds(profile) * SAMPLE_RATE).toInt()
        val out = FloatArray(frames)

        val noise = Noise(seed)
        val roar = Resonator()
        val body = Resonator()
        val gurgle = Resonator()
        val hiss = Resonator()

        for (frame in 0 until frames) {
            val t = frame.toDouble() / SAMPLE_RATE
            val n = noise.next()
            var sample = 0.0

            // The handle bottoming out.
            if (t < 0.22) {
                sample += sin(2 * PI * profile.clunkFrequency * t) * exp(-t * 32) * 0.45
                sample += n * exp(-t * 85) * 0.30
            }

            // The main event: bright at first, dropping as the bowl empties.
            val roarLevel = envelope(t, 0.08 * s, 0.45 * s, 1.85 * s, 2.85 * s)
            if (roarLevel > 0) {
                val sweep = profile.roarFrom -
                    (profile.roarFrom - profile.roarTo) * clamp((t - 0.18 * s) / (1.7 * s))
                roar.tune(sweep, 1.15)
                body.tune(profile.bodyFrequency, 0.8)
                sample += (roar.bandPass(n) * 0.55 + body.lowPass(n) * 0.85) * roarLevel
            }

            // The uneven glugging underneath it.
            val gurgleLevel = envelope(t, 0.45 * s, 0.85 * s, 1.95 * s, 2.55 * s)
            if (gurgleLevel > 0) {
                gurgle.tune(profile.gurgleCentre + profile.gurgleSwing * sin(2 * PI * 1.7 * t), 5.0)
                val wobble = 0.55 + 0.45 * sin(2 * PI * (5.5 + 2.5 * sin(2 * PI * 0.7 * t)) * t)
                sample += gurgle.bandPass(n) * gurgleLevel * wobble * 0.85
            }

            // The tank filling back up, rising in pitch as it gets full.
            val hissLevel = envelope(t, 2.00 * s, 2.35 * s, 3.30 * s, 4.15 * s)
            if (hissLevel > 0) {
                hiss.tune(
                    profile.hissFrom +
                        (profile.hissTo - profile.hissFrom) * clamp((t - 2.2 * s) / (1.6 * s)),
                    0.9,
                )
                sample += hiss.bandPass(n) * hissLevel * 0.30
            }

            // The float valve shutting off.
            if (t > 4.10 * s) {
                val since = t - 4.10 * s
                sample += sin(2 * PI * profile.valveFrequency * since) * exp(-since * 38) * 0.32
            }

            // A little fanfare, for the rare ones.
            if (golden) {
                for (step in FANFARE.indices) {
                    // The run itself keeps its tempo; only where it lands moves.
                    val start = 2.15 * s + step * 0.13
                    if (t <= start) continue
                    val since = t - start
                    sample += sin(2 * PI * FANFARE[step] * since) * exp(-since * 3.2) * 0.15
                }
            }

            // Soft clip, so nothing ever spits.
            out[frame] = (tanh(sample * 0.95) * 0.55).toFloat()
        }

        return out
    }

    private fun clamp(x: Double): Double = x.coerceIn(0.0, 1.0)

    /** Rise, hold, fall — with smoothed corners so nothing clicks. */
    private fun envelope(t: Double, from: Double, peak: Double, hold: Double, until: Double): Double {
        if (t <= from || t >= until) return 0.0
        if (t < peak) return smooth((t - from) / maxOf(peak - from, 0.001))
        if (t < hold) return 1.0
        return smooth(1 - (t - hold) / maxOf(until - hold, 0.001))
    }

    private fun smooth(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }
}

/**
 * A two-pole state-variable filter. Turns flat noise into something that sounds like
 * it is coming out of a pipe.
 */
private class Resonator {
    private var low = 0.0
    private var band = 0.0
    private var f = 0.1
    private var damping = 1.0

    fun tune(frequency: Double, q: Double) {
        val bounded = frequency.coerceIn(20.0, FlushSynth.SAMPLE_RATE / 6.0)
        f = 2 * sin(PI * bounded / FlushSynth.SAMPLE_RATE)
        damping = 1 / maxOf(q, 0.5)
    }

    fun bandPass(input: Double): Double {
        step(input)
        return band
    }

    fun lowPass(input: Double): Double {
        step(input)
        return low
    }

    private fun step(input: Double) {
        val high = input - low - damping * band
        band = (band + f * high).coerceIn(-4.0, 4.0)
        low = (low + f * band).coerceIn(-4.0, 4.0)
    }
}

/**
 * A small, fast, repeatable noise source.
 *
 * Repeatable matters: the same seed gives the same flush every launch, so a take that
 * sounds good stays sounding good. Kotlin's Long is signed where Swift's was not, but
 * the wrapping arithmetic is the same bits either way — only the shift has to be told
 * to be logical.
 */
private class Noise(seed: Long) {
    private var state: Long = seed * MULTIPLIER + INCREMENT

    fun next(): Double {
        state = state * MULTIPLIER + INCREMENT
        val bits = (state ushr 33) and 0xFFFF_FFFFL
        return bits.toDouble() / 4_294_967_295.0 * 2 - 1
    }

    private companion object {
        const val MULTIPLIER = 6_364_136_223_846_793_005L
        const val INCREMENT = 1_442_695_040_888_963_407L
    }
}
