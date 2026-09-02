package com.tomchapman.flushsimulator.device

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tomchapman.flushsimulator.core.FlushAudio
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.FlushSynth
import com.tomchapman.flushsimulator.core.Settings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Somewhere to put the samples.
 *
 * All the arithmetic is [FlushSynth], in `core`; this only turns floats into 16-bit
 * PCM and feeds them to an [AudioTrack]. Takes are still rendered whole on a
 * background thread, which keeps that arithmetic well away from the audio thread —
 * the same reason the Swift rendered into buffers rather than synthesising live.
 *
 * Streaming rather than a static buffer. A static track holds the entire clip and has
 * to be rewound between plays, which is where its first-play glitch comes from; a
 * stream track is a small ring the clip is fed through, and one track serves every
 * flush for the life of the app.
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

    /**
     * Bumped by anything that supersedes what is sounding. The writer checks it every
     * chunk, so a flush can be cut off part-way even though `write` blocks.
     */
    private val generation = AtomicInteger(0)

    @Volatile
    private var track: AudioTrack? = null

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

        // Claimed here rather than on the queue: a write already in progress has to
        // learn it has been superseded before the new take waits behind it.
        val mine = generation.incrementAndGet()
        silence()

        queue.execute {
            if (renderedVoice != voice || generation.get() != mine) return@execute
            val samples = if (golden) this.golden else ordinary.randomOrNull(random)
            if (samples != null) stream(samples, mine)
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        silence()
    }

    /**
     * Feeds one take through the track.
     *
     * `write` blocks until the ring has room, which is exactly what paces this loop —
     * and why the generation is checked between chunks rather than only at the start.
     */
    private fun stream(samples: ShortArray, mine: Int) {
        val device = track ?: build()?.also { track = it } ?: return

        runCatching { device.play() }.onFailure { return }

        var offset = 0
        while (offset < samples.size && generation.get() == mine) {
            val wrote = device.write(samples, offset, minOf(CHUNK, samples.size - offset))
            if (wrote <= 0) break        // flushed out from under us, or the track died
            offset += wrote
        }

        // Let whatever is already in the ring play out rather than cutting the tail.
        // The track is kept, not released: the next flush reuses it.
        if (generation.get() == mine) runCatching { device.stop() }
    }

    /** Drops anything queued and stops the sound now. Safe from any thread. */
    private fun silence() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
        }
    }

    private fun build(): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(
            FlushSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) return null

        return runCatching {
            AudioTrack.Builder()
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
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Room to stay ahead of the mixer without adding audible latency to a
                // sound that is meant to answer a finger.
                .setBufferSizeInBytes(maxOf(minBytes * 2, MIN_RING_BYTES))
                .build()
        }.getOrNull()
    }

    internal companion object {
        const val KEY_MUTED = "flushMuted"

        /** Samples handed over per `write`. Small enough to notice an interruption. */
        private const val CHUNK = 4_096

        private const val MIN_RING_BYTES = 16_384

        /** Full-scale float to full-scale 16-bit, clamped so nothing wraps. */
        internal fun pcm(samples: FloatArray) = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
