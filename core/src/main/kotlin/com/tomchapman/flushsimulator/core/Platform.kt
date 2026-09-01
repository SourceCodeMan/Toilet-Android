package com.tomchapman.flushsimulator.core

/**
 * The seams between the rules and the device.
 *
 * The Swift reached straight for `UserDefaults.standard`, `FlushAudio.shared` and
 * `Haptics.shared`. Those are the three things that cannot exist in a unit test, so
 * here they are interfaces the engine is handed. Android supplies real ones; the
 * tests supply fakes; and the whole of this module compiles and runs on a plain JVM
 * with no emulator in sight.
 */

/** Key-value storage. `UserDefaults` on the other side of the fence. */
interface Settings {
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getDouble(key: String, default: Double = 0.0): Double
    fun putDouble(key: String, value: Double)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/**
 * The rumble. Implemented against `Vibrator` on Android; see the README for why
 * sharpness has nowhere to go.
 */
interface Haptics {
    /** The lever catching, the instant you press it. */
    fun tick() {}
    /** A refusal: the bowl is busy, or blocked, or the fixture is not yours yet. */
    fun thud() {}
    /** The long swell of a flush. [scale] is the fixture's [FlushProfile.timeScale]. */
    fun flush(golden: Boolean, scale: Double) {}

    /** Does nothing, quietly. */
    object None : Haptics
}

/** The synthesised flush. Rendered into an `AudioTrack` on Android. */
interface FlushAudio {
    fun prepare(voice: FlushProfile) {}
    fun play(golden: Boolean, voice: FlushProfile) {}
    fun stop() {}

    /** Does nothing, quietly. */
    object None : FlushAudio
}

/** Wall time, injectable so a test can decide what "now" is. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
