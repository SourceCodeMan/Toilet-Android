package com.tomchapman.flushsimulator.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tomchapman.flushsimulator.core.HapticPattern
import com.tomchapman.flushsimulator.core.HapticStep
import com.tomchapman.flushsimulator.core.Haptics

/**
 * The rumble.
 *
 * The pattern itself is [HapticPattern], in `core`, so the shape of a buzz can be
 * checked by a test even though nobody can feel one. This only hands it over.
 *
 * Two things are worth knowing. Plenty of hardware cannot vary amplitude at all
 * (`hasAmplitudeControl`), and on those the swell collapses to on-or-off — so a
 * flush there is a rhythm rather than a shape. And nothing on Android carries
 * CoreHaptics' *sharpness*, so the crisp tick and the dull thud are told apart only
 * by their length, which is a poorer distinction than the iOS one.
 */
class AndroidHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = run {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    }

    private val hasAmplitude: Boolean = vibrator?.hasAmplitudeControl() == true

    override fun tick() = play(HapticPattern.tick())

    override fun thud() = play(HapticPattern.thud())

    override fun flush(golden: Boolean, scale: Double) = play(HapticPattern.flush(golden, scale))

    private fun play(steps: List<HapticStep>) {
        val device = vibrator ?: return
        if (steps.isEmpty()) return

        val timings = LongArray(steps.size) { steps[it].millis }
        val effect = if (hasAmplitude) {
            VibrationEffect.createWaveform(timings, IntArray(steps.size) { steps[it].amplitude }, -1)
        } else {
            // No amplitude control, so the swell has to become a rhythm: rungs that
            // would have been faint are simply off.
            VibrationEffect.createWaveform(timings, onOff(steps), -1)
        }

        // A missed buzz is not worth bothering anyone about.
        runCatching { device.vibrate(effect) }
    }

    /** Amplitudes collapsed to on or off, for hardware that only knows those. */
    private fun onOff(steps: List<HapticStep>) = IntArray(steps.size) {
        if (steps[it].amplitude >= HapticPattern.MAX_AMPLITUDE / 3) HapticPattern.MAX_AMPLITUDE else 0
    }
}
