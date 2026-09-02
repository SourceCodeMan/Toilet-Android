package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.toSize
import com.tomchapman.flushsimulator.core.FlushGrade
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.Palette
import kotlinx.coroutines.delay

/**
 * The toilet, and the only control in the app.
 *
 * @param flushStartMillis when the running flush began, or null if the bowl is at rest.
 * @param onPull called on release, with how the handle was actually pulled.
 */
@Composable
fun Toilet(
    palette: Palette,
    profile: FlushProfile,
    grime: Double,
    flushStartMillis: Long?,
    onPull: (FlushGrade) -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed = flushElapsedSeconds(flushStartMillis)
    val restClock = restClockSeconds(running = flushStartMillis == null)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // How far the finger has pushed the handle, and when it went down. The grade is
    // taken from the touch timestamps rather than a clock of our own: those are what
    // actually measure the gesture, they survive a dropped frame, and they are what a
    // test can drive.
    var drag by remember { mutableStateOf(0.0) }
    var pressedAt by remember { mutableStateOf<Long?>(null) }

    // A gesture interrupted by a call or the app going away may never deliver its
    // release, which would leave the meter counting up for the rest of the session.
    DisposableEffect(Unit) {
        onDispose {
            drag = 0.0
            pressedAt = null
        }
    }

    val held = holdSeconds(pressedAt)

    Canvas(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val (scale, origin) = toiletTransform(size.toSize())
                    val design = (down.position - origin) / scale
                    if (!Toilet.hitArea.contains(design)) return@awaitEachGesture

                    down.consume()
                    pressedAt = down.uptimeMillis
                    // The lever bottoms out quickly; after that it is all hold.
                    drag = 0.55
                    onPress()

                    val up = waitForUpOrCancellation()
                    pressedAt = null
                    drag = 0.0

                    // A gesture the system took away — a notification shade, a call —
                    // is not a flush. Only a real release pulls the handle.
                    if (up != null) {
                        val heldMillis = up.uptimeMillis - down.uptimeMillis
                        onPull(FlushGrade.forHold(heldMillis / 1_000.0))
                    }
                }
            }
            .semantics {
                contentDescription =
                    "Flush handle. Hold, and let go inside the window for a perfect flush."
                // A screen reader cannot hold a lever, so it always gets a clean one.
                onClick {
                    onPull(FlushGrade.Good)
                    true
                }
            },
    ) {
        drawToilet(
            elapsed = elapsed,
            palette = palette,
            profile = profile,
            grime = grime,
            drag = drag,
            restClock = restClock,
        )
        if (held != null) {
            val (scale, origin) = toiletTransform(size)
            drawHoldMeter(held, palette, measurer, scale, origin, density.density)
        }
    }
}

/**
 * The window you are aiming for, while your finger is still down.
 *
 * Sits on the tank face just above the handle, where there is about seventeen points
 * to work with — hence the flat layout, with the verdict beside the bar rather than
 * stacked over it.
 */
private fun DrawScope.drawHoldMeter(
    held: Double,
    palette: Palette,
    measurer: TextMeasurer,
    scale: Float,
    origin: Offset,
    density: Float,
) {
    val track = 112f
    val fill = (held / FlushGrade.METER_SPAN).coerceAtMost(1.0).toFloat()
    val grade = FlushGrade.forHold(held)
    val colour = when (grade) {
        FlushGrade.Weak -> palette.ink.toColor().copy(alpha = 0.45f)
        FlushGrade.Good -> palette.accent.toColor()
        FlushGrade.Perfect -> Color(red = 0.16f, green = 0.72f, blue = 0.44f)
        FlushGrade.Overheld -> Color(red = 0.86f, green = 0.34f, blue = 0.24f)
    }

    // Centred on the tank face above the lever, in design coordinates.
    val left = origin.x + (116f - track / 2) * scale
    val top = origin.y + (51f - 4.5f) * scale
    val height = 9f * scale
    val radius = CornerRadius(height / 2)

    drawRoundRect(
        color = palette.ink.toColor().copy(alpha = 0.16f),
        topLeft = Offset(left, top),
        size = Size(track * scale, height),
        cornerRadius = radius,
    )

    // The window, marked out so you can see what you are aiming at.
    val windowStart = (FlushGrade.PERFECT_FROM / FlushGrade.METER_SPAN).toFloat()
    val windowSpan = ((FlushGrade.PERFECT_UNTIL - FlushGrade.PERFECT_FROM) / FlushGrade.METER_SPAN).toFloat()
    drawRoundRect(
        color = palette.accent.toColor().copy(alpha = 0.30f),
        topLeft = Offset(left + track * windowStart * scale, top),
        size = Size(track * windowSpan * scale, height),
        cornerRadius = radius,
    )

    drawRoundRect(
        color = colour,
        topLeft = Offset(left, top),
        size = Size(maxOf(track * fill, 5f) * scale, height),
        cornerRadius = radius,
    )

    val label = if (grade == FlushGrade.Weak) "hold" else grade.label
    val style = TextStyle(
        color = colour,
        fontSize = TextUnit(9f * scale / density, TextUnitType.Sp),
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
    )
    val measured = measurer.measure(label, style)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(left + (track + 7) * scale, top + height / 2 - measured.size.height / 2),
    )
}

// Clocks

/** Seconds since the flush began, ticking every frame while one runs. */
@Composable
private fun flushElapsedSeconds(startMillis: Long?): Double? {
    if (startMillis == null) return null
    var seconds by remember(startMillis) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(startMillis) {
        val begin = withFrameNanos { it }
        while (true) {
            seconds = (withFrameNanos { it } - begin) / 1_000_000_000.0
        }
    }
    return seconds
}

/**
 * A slow clock for the pool's shimmer.
 *
 * Nothing animates while nothing is happening: at rest only the pool keeps time, and
 * twelve frames a second is plenty for a shimmer.
 */
@Composable
private fun restClockSeconds(running: Boolean): Double {
    var seconds by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1_000L / 12)
            seconds += 1.0 / 12
        }
    }
    return seconds
}

/**
 * How long the finger has been down, for the meter only.
 *
 * Ticks off the frame clock because pointer events stop arriving the moment the
 * finger stops moving, and a meter that freezes mid-hold is worse than useless. The
 * grade itself comes from the touch timestamps, not from here.
 */
@Composable
private fun holdSeconds(pressedAt: Long?): Double? {
    if (pressedAt == null) return null
    var seconds by remember(pressedAt) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(pressedAt) {
        val begin = withFrameNanos { it }
        while (true) {
            seconds = (withFrameNanos { it } - begin) / 1_000_000_000.0
        }
    }
    return seconds
}
