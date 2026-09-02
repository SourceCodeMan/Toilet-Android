package com.tomchapman.flushsimulator.core

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Writes every voice out as a wav, for listening to.
 *
 * Not an assertion — the same bargain as the screenshots on the other side of the
 * fence. The only useful question about a noise is what it sounds like, and no test
 * can answer that, so this puts the answer somewhere a person can play it.
 *
 * `core/build/audio/` after `./gradlew :core:test`.
 */
class WriteTakesTest {

    @Test
    fun `write the takes out for listening`() {
        val out = File("build/audio").apply { mkdirs() }

        for (fixture in Fixture.all) {
            write(
                File(out, "flush-${fixture.id}.wav"),
                FlushSynth.render(fixture.profile, FlushSynth.ORDINARY_SEEDS[0], golden = false),
            )
        }
        write(
            File(out, "flush-golden.wav"),
            FlushSynth.render(FlushProfile.Standard, FlushSynth.GOLDEN_SEED, golden = true),
        )
        // The three takes of the standard toilet, so the variation is audible.
        FlushSynth.ORDINARY_SEEDS.forEachIndexed { i, seed ->
            write(File(out, "take-${i + 1}.wav"), FlushSynth.render(FlushProfile.Standard, seed, false))
        }
    }

    /** Mono 16-bit PCM, by hand. A wav header is 44 bytes and needs no library. */
    private fun write(file: File, samples: FloatArray) {
        val bytes = samples.size * 2
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            fun i32(v: Int) =
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())

            fun i16(v: Int) =
                out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())

            out.writeBytes("RIFF"); i32(36 + bytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); i32(16); i16(1); i16(1)
            i32(FlushSynth.SAMPLE_RATE); i32(FlushSynth.SAMPLE_RATE * 2); i16(2); i16(16)
            out.writeBytes("data"); i32(bytes)
            for (s in samples) i16((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt())
        }
    }
}
