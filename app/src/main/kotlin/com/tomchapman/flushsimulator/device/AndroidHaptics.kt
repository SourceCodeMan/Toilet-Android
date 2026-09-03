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

        // Where strength cannot be varied, it is varied in time instead.
        val playable = if (hasAmplitude) steps else HapticPattern.pulsed(steps)
        if (playable.isEmpty()) return

        val effect = VibrationEffect.createWaveform(
            LongArray(playable.size) { playable[it].millis },
            IntArray(playable.size) { playable[it].amplitude },
            -1,
        )

        // A missed buzz is not worth bothering anyone about.
        runCatching { device.vibrate(effect) }
    }
}
