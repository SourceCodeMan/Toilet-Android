package com.tomchapman.flushsimulator.device

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tomchapman.flushsimulator.core.FlushAudio
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.FlushSynth
import com.tomchapman.flushsimulator.core.Settings
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Somewhere to put the samples.
 *
 * All the arithmetic is [FlushSynth], in `core`; this only turns floats into 16-bit
 * PCM and hands them to an [AudioTrack]. Takes are rendered whole on a background
 * thread and played in one go, which keeps every bit of that arithmetic well away
 * from the audio thread — the same reason the Swift rendered into buffers rather than
 * synthesising live.
 *
 * `USAGE_GAME` rather than a media usage: a flush should follow the game volume, and
 * Android does not silence games with the ringer switch the way iOS does, so the
 * Swift's `.playback`/`.mixWithOthers` dance has no counterpart to make here.
 */
class AndroidFlushAudio(
    private val settings: Settings,
    private val random: Random = Random.Default,
) : FlushAudio {

    private val queue = Executors.newSingleThreadExecutor { r ->
        Thread(r, "flush-audio").apply { isDaemon = true }
    }

    /** Which voice is sitting in the buffers, or null if none is. */
    private var renderedVoice: FlushProfile? = null
    private var ordinary: List<ShortArray> = emptyList()
    private var golden: ShortArray? = null
    private var playing: AudioTrack? = null

    override var isMuted: Boolean
        get() = settings.getInt(KEY_MUTED) == 1
        set(value) {
            settings.putInt(KEY_MUTED, if (value) 1 else 0)
            if (value) stop()
        }

    /**
     * Renders one fixture's voice. Safe to call more than once, and cheap when the
     * voice asked for is already loaded.
     */
    override fun prepare(voice: FlushProfile) {
        queue.execute {
            if (renderedVoice == voice) return@execute
            val takes = FlushSynth.ORDINARY_SEEDS.map { pcm(FlushSynth.render(voice, it, golden = false)) }
            val rare = pcm(FlushSynth.render(voice, FlushSynth.GOLDEN_SEED, golden = true))
            ordinary = takes
            golden = rare
            renderedVoice = voice
        }
    }

    override fun play(golden: Boolean, voice: FlushProfile) {
        if (isMuted) return
        // Keep preparation and this request ordered on the one thread, so a pull
        // straight after launch plays as soon as rendering ends.
        prepare(voice)
        queue.execute {
            if (renderedVoice != voice) return@execute
            val samples = if (golden) this.golden else ordinary.randomOrNull(random)
            if (samples != null) start(samples)
        }
    }

    override fun stop() {
        queue.execute { release() }
    }

    private fun start(samples: ShortArray) {
        release()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(FlushSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            // The whole take is already in hand, so it goes in as one static buffer
            // rather than being streamed a block at a time.
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        playing = track
    }

    private fun release() {
        playing?.run {
            // Stopping a static track that never started throws, and a flush nobody
            // heard is not worth an exception.
            runCatching { pause() }
            runCatching { flush() }
            release()
        }
        playing = null
    }

    private fun pcm(samples: FloatArray) = ShortArray(samples.size) { i ->
        (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
    }

    private companion object {
        const val KEY_MUTED = "flushMuted"
    }
}
