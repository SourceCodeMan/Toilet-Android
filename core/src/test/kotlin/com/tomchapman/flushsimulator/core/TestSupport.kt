package com.tomchapman.flushsimulator.core

import kotlin.random.Random

/** An in-memory [Settings]. */
class MapSettings(initial: Map<String, Any> = emptyMap()) : Settings {
    private val values = LinkedHashMap<String, Any>(initial)

    override fun getInt(key: String, default: Int) = (values[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getDouble(key: String, default: Double) = (values[key] as? Double) ?: default
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/**
 * A [Random] that hands back the numbers a test asks for, in order, then a default
 * for everything after.
 *
 * The engine rolls twice per flush — once for gold on the way in, once for a clog on
 * the way out — so a test that wants a golden flush that does not block queues
 * `0.0, 1.0` and says exactly what it means.
 */
class QueuedRandom(
    vararg doubles: Double,
    private val default: Double = 1.0,
) : Random() {
    private val queue = ArrayDeque(doubles.toList())

    /** Whether the next sheet off the roll is the one-in-a-hundred. Off by default. */
    var cash = false

    override fun nextBits(bitCount: Int): Int = 0
    override fun nextDouble(): Double = if (queue.isEmpty()) default else queue.removeFirst()

    // The cash roll asks for an int below the odds and wins on zero, so a test that
    // has not asked for money must never get it by accident.
    override fun nextInt(until: Int): Int = if (cash) 0 else until - 1
}

/** Records what the engine asked the device to do. */
class RecordingHaptics : Haptics {
    val calls = mutableListOf<String>()
    override fun tick() { calls += "tick" }
    override fun thud() { calls += "thud" }
    override fun flush(golden: Boolean, scale: Double) { calls += "flush(golden=$golden)" }
}

class RecordingAudio : FlushAudio {
    override var isMuted: Boolean = false
    val calls = mutableListOf<String>()
    override fun prepare(voice: FlushProfile) { calls += "prepare(${voice.duration})" }
    override fun play(golden: Boolean, voice: FlushProfile) { calls += "play(golden=$golden)" }
    override fun stop() { calls += "stop" }
}

/** A clock a test winds by hand. */
class FakeClock(var millis: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = millis
}
