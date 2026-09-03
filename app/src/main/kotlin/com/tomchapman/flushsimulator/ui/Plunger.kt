package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tomchapman.flushsimulator.core.Upkeep
import kotlin.math.hypot

/**
 * The plunger, which lives on the floor rather than appearing as a button.
 *
 * Drag it over the bowl and it seats; push down from there and each stroke is a pump.
 * Let go anywhere else and it walks back to its corner. The old red PLUNGE button did
 * the same job in one tap, which is exactly why it was not worth doing.
 *
 * The stage owns [offset] and does the positioning, so this is a plain sized box with
 * a gesture on it. All coordinates are stage design units; [scale] turns them into dp.
 */
object Plunger {
    const val WIDTH = 104f
    const val HEIGHT = 176f

    /** The rubber hangs this far below the middle of the box, so seating is measured from there. */
    const val CUP_DROP = 46f

    /**
     * How close the rubber has to get to the bowl to count as seated.
     *
     * Generous on purpose: the bowl is the only thing worth plunging, so there is
     * nothing to be precise about, and a tight radius reads as the plunger sitting in
     * the bowl while refusing to bite.
     */
    const val SEAT_RADIUS = 105f

    /** How far you have to push down for one stroke to register. */
    const val STROKE_TRAVEL = 22f
}

/**
 * @param bowl where the bowl sits, in stage units.
 * @param home where it leans when not needed, in stage units — the centre of the box.
 * @param offset where it has been dragged to, relative to [home]. Owned by the stage.
 */
@Composable
fun Plunger(
    isClogged: Boolean,
    plunges: Int,
    isBlockedByPaper: Boolean,
    bowl: Offset,
    home: Offset,
    offset: Offset,
    onOffset: (Offset) -> Unit,
    scale: Float,
    onPump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val unit = scale * density

    // Where it settles when the finger lifts, and the state of the current stroke.
    // These are plain remembered values rather than compose state: a gesture writes
    // them and reads them back on the very next callback, and nothing else needs to
    // recompose when they change.
    val parked = remember { arrayOf(Offset.Zero) }
    val stroke = remember { Stroke() }

    // Read rather than closed over, so the detector below can be started once. Keying
    // it on `isClogged` restarts it on the fifth pump — mid-gesture, finger still down.
    val liveClogged by rememberUpdatedState(isClogged)
    val liveBlocked by rememberUpdatedState(isBlockedByPaper)

    fun cupFor(at: Offset) = Offset(home.x + at.x, home.y + at.y + Plunger.CUP_DROP)
    fun seatedAt(at: Offset): Boolean {
        val c = cupFor(at)
        return hypot(c.x - bowl.x, c.y - bowl.y) < Plunger.SEAT_RADIUS
    }
    fun goHome() {
        parked[0] = Offset.Zero
        onOffset(Offset.Zero)
    }

    // Once the blockage is gone there is nothing to stand in the bowl for. Without
    // this it stays parked on the seat for the rest of the session.
    LaunchedEffect(isClogged) {
        if (!isClogged) goHome()
    }

    val isSeated = seatedAt(offset)

    Canvas(
        modifier
            .alpha(if (isClogged) 1f else 0.7f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { stroke.reset() },
                    onDragEnd = {
                        // Only an actual blockage earns a place in the bowl, and only if
                        // it seated. Anything else goes back to the corner — otherwise a
                        // stray drag during ordinary play leaves it standing on the seat.
                        if (liveClogged && stroke.seated) {
                            parked[0] = stroke.restingOffset
                            onOffset(stroke.restingOffset)
                        } else {
                            goHome()
                        }
                        stroke.reset()
                    },
                    onDragCancel = {
                        goHome()
                        stroke.reset()
                    },
                ) { change, delta ->
                    change.consume()
                    stroke.travel += Offset(delta.x / unit, delta.y / unit)
                    stroke.fingerY += delta.y / unit
                    val moved = parked[0] + stroke.travel
                    onOffset(moved)

                    // Seating latches. A downstroke pushes the cup well past the bowl's
                    // centre, so re-testing every frame unseats it mid-pump and the
                    // stroke never completes — and physically, shoving a seated
                    // plunger down is the whole point, not a reason for it to pop out.
                    if (!stroke.seated && seatedAt(moved)) {
                        stroke.seated = true
                        stroke.restingOffset = moved
                    }
                    if (!liveClogged || liveBlocked || !stroke.seated) {
                        stroke.anchor = null
                        return@detectDragGestures
                    }
                    val anchor = stroke.anchor
                    if (anchor == null) {
                        stroke.anchor = stroke.fingerY
                        return@detectDragGestures
                    }
                    if (stroke.fingerY - anchor > Plunger.STROKE_TRAVEL) {
                        onPump()                    // a completed downstroke
                        stroke.anchor = stroke.fingerY
                    } else if (anchor - stroke.fingerY > Plunger.STROKE_TRAVEL) {
                        stroke.anchor = stroke.fingerY   // came back up, ready for the next
                    }
                }
            }
            .semantics {
                contentDescription = "Plunger. Drag onto the bowl, then push down to plunge."
                stateDescription = when {
                    !isClogged -> "Not needed right now"
                    isBlockedByPaper -> "Blocked by paper still attached"
                    else -> "$plunges of ${Upkeep.PLUNGES_TO_CLEAR} pumps"
                }
                customActions = listOf(CustomAccessibilityAction("Pump") { onPump(); true })
            },
    ) {
        val s = size.width / Plunger.WIDTH
        scale(s, s, pivot = Offset.Zero) {
            // Seated and biting: it sinks a little, so the pump reads.
            val sink = if (isSeated && isClogged) 1.05f else 1f
            scale(sink, sink, pivot = Offset(Plunger.WIDTH / 2, Plunger.HEIGHT)) {
                drawPlunger()
            }
        }
    }
}

/** The stroke in progress. A plain object, mutated by the gesture and read straight back. */
private class Stroke {
    /** How far the drag has moved, in stage units. */
    var travel: Offset = Offset.Zero
    /** Vertical travel alone, which is what a pump is measured against. */
    var fingerY = 0f
    var anchor: Float? = null
    /** Set once the cup reaches the bowl, and held for the rest of the drag. */
    var seated = false
    /** Where it first sat down, so releasing puts it back there rather than wherever the last downstroke ended. */
    var restingOffset: Offset = Offset.Zero

    fun reset() {
        travel = Offset.Zero
        fingerY = 0f
        anchor = null
        seated = false
    }
}

private fun DrawScope.drawPlunger() {
    val cx = Plunger.WIDTH / 2
    val cy = Plunger.HEIGHT / 2

    // Shaft, with the cup overlapping its foot by three units, as the VStack did.
    val shaftTop = cy - (92f + 40f - 3f) / 2
    translate(1f, 2f) {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(cx - 6f, shaftTop),
            size = Size(12f, 92f),
            cornerRadius = CornerRadius(6f),
        )
    }
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(red = 0.72f, green = 0.53f, blue = 0.32f), Color(red = 0.46f, green = 0.31f, blue = 0.16f)),
            startX = cx - 6f,
            endX = cx + 6f,
        ),
        topLeft = Offset(cx - 6f, shaftTop),
        size = Size(12f, 92f),
        cornerRadius = CornerRadius(6f),
    )

    // The bell-shaped rubber cup: wide flared lip, narrow neck.
    val cupTop = shaftTop + 92f - 3f
    val cup = cupPath(Offset(cx - 23f, cupTop), 46f, 40f)
    translate(0f, 3f) {
        drawPath(cup, color = Color.Black.copy(alpha = 0.28f))
    }
    drawPath(
        cup,
        brush = Brush.verticalGradient(
            listOf(Color(red = 0.78f, green = 0.22f, blue = 0.18f), Color(red = 0.44f, green = 0.09f, blue = 0.07f)),
            startY = cupTop,
            endY = cupTop + 40f,
        ),
    )
}

private fun cupPath(at: Offset, w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.34f, 0f)
    lineTo(w * 0.66f, 0f)
    cubicTo(w * 0.80f, h * 0.18f, w * 0.99f, h * 0.46f, w, h * 0.82f)
    quadraticTo(w * 0.5f, h * 1.16f, 0f, h * 0.82f)
    cubicTo(w * 0.01f, h * 0.46f, w * 0.20f, h * 0.18f, w * 0.34f, 0f)
    close()
    translate(at)
}
