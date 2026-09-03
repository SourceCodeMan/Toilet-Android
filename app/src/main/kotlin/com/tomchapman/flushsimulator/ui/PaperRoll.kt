package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.tomchapman.flushsimulator.core.Palette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The roll on the wall, and the sheet you tear off it.
 *
 * Paper used to be a stepper you set once and forgot. It is now the thing you do
 * before every flush: draw the sheet down to the length you want, then swipe across to
 * tear it off. Forget the tear and the bowl keeps drawing off the roll for the whole
 * flush — see `FlushState.loadedPaper`, which is where that gets expensive.
 *
 * Drawn in a 96x232 design space, the iOS one, and given a slot of exactly that shape
 * scaled by [scale] so every coordinate below means what it did.
 */
object PaperRoll {
    const val WIDTH = 96f
    const val HEIGHT = 232f

    /** How far one square hangs, in design units. */
    const val SQUARE = 26f
    private const val SHEET_WIDTH = 46f
}

/**
 * @param pulled squares hanging (or, once torn, sitting ready).
 * @param isTrailing a flush dragged the roll in and it is still attached.
 * @param isCash this one came off as hundreds. One roll in a hundred does.
 * @param scale design units to dp.
 */
@Composable
fun PaperRoll(
    pulled: Int,
    isCut: Boolean,
    isTrailing: Boolean,
    isCash: Boolean,
    palette: Palette,
    scale: Float,
    onPull: (Int) -> Unit,
    onCut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val hanging = if (isCut) 0 else pulled

    // The gesture reads these rather than closing over them, because it must not be
    // restarted by its own effects — see the `pointerInput(Unit)` below.
    val livePulled by rememberUpdatedState(pulled)
    val liveCut by rememberUpdatedState(isCut)

    // Where the drag began and what it has done since. Plain fields rather than
    // compose state: the gesture writes them and reads them back on the very next
    // callback, and none of them changes what is drawn, so recomposing fifty times
    // across one swipe would be pure waste.
    val drag = remember { Pull() }

    val stateLine = when {
        isCash -> "$hanging hundred dollar bills hanging. Flush them."
        isTrailing -> "Caught in the bowl. Tear it free."
        isCut -> "$pulled squares torn off and ready"
        hanging == 0 -> "Nothing pulled yet"
        else -> "$hanging squares hanging, not torn"
    }

    Canvas(
        modifier
            // Keyed on nothing, deliberately. Keying this on `pulled` restarts the
            // detector the moment the first square comes off — while the finger is
            // still down — and every later move is dropped. A synthetic one-jump drag
            // hides it, because the jump finishes before the restart lands; a real
            // finger sends fifty small moves and only the first one survived. So the
            // detector is started once and reads the live values instead.
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { drag.reset() },
                    onDragEnd = { drag.reset() },
                    onDragCancel = { drag.reset() },
                ) { change, delta ->
                    change.consume()
                    drag.travel += delta
                    val unit = scale * density
                    val across = abs(drag.travel.x) / unit
                    val down = abs(drag.travel.y) / unit

                    // A decisive sideways swipe tears, but only once per gesture and
                    // only when there is something hanging to tear.
                    if (!drag.torn && livePulled > 0 && across > 34 && across > down * 1.3) {
                        drag.torn = true
                        onCut()
                        return@detectDragGestures
                    }
                    if (drag.torn || liveCut) return@detectDragGestures

                    if (drag.base == null) drag.base = if (liveCut) 0 else livePulled
                    val drawn = (drag.travel.y / unit / PaperRoll.SQUARE).roundToInt()
                    onPull((drag.base ?: 0) + drawn)
                }
            }
            .semantics {
                contentDescription =
                    "Toilet paper. Swipe up or down to pull squares off the roll, across to tear."
                stateDescription = stateLine
                customActions = listOf(
                    CustomAccessibilityAction("One square more") { onPull(hanging + 1); true },
                    CustomAccessibilityAction("One square less") { onPull(hanging - 1); true },
                    CustomAccessibilityAction("Tear off") { onCut(); true },
                )
            },
    ) {
        val s = size.width / PaperRoll.WIDTH
        scale(s, s, pivot = Offset.Zero) {
            drawRoll(hanging, pulled, isCut, isTrailing, isCash, palette, measurer, s, density)
        }
    }
}

private val BILL_FACE = Color(red = 0.42f, green = 0.60f, blue = 0.44f)
private val BILL_INK = Color(red = 0.16f, green = 0.31f, blue = 0.20f)
private val TRAILING = Color(red = 0.86f, green = 0.34f, blue = 0.24f)

/** One drag of the sheet. Mutated by the gesture and read straight back. */
private class Pull {
    var travel: Offset = Offset.Zero
    /** Squares hanging when this drag began, so a pull is relative rather than absolute. */
    var base: Int? = null
    /** One tear per gesture, however far the finger keeps going afterwards. */
    var torn = false

    fun reset() {
        travel = Offset.Zero
        base = null
        torn = false
    }
}

private fun DrawScope.drawRoll(
    hanging: Int,
    pulled: Int,
    isCut: Boolean,
    isTrailing: Boolean,
    isCash: Boolean,
    palette: Palette,
    measurer: androidx.compose.ui.text.TextMeasurer,
    unitPx: Float,
    density: Float,
) {
    val cx = PaperRoll.WIDTH / 2
    val sheetWidth = 46f
    val sheetLength = hanging * PaperRoll.SQUARE

    // The sheet hanging off the roll, perforated square by square. Drawn first so the
    // roll sits over its top edge.
    if (sheetLength > 0) {
        val left = cx - sheetWidth / 2
        val top = 52f
        translate(0f, 0f) {
            // Its shadow.
            drawRoundRect(
                color = palette.porcelainShadow.toColor().copy(alpha = 0.22f),
                topLeft = Offset(left + 1, top + 2),
                size = Size(sheetWidth, sheetLength),
                cornerRadius = CornerRadius(3f),
            )
            drawRoundRect(
                brush = if (isCash) {
                    Brush.horizontalGradient(
                        listOf(BILL_FACE.copy(alpha = 0.95f), BILL_FACE),
                        startX = left,
                        endX = left + sheetWidth,
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White, palette.porcelainMid.toColor()),
                        startX = left,
                        endX = left + sheetWidth,
                    )
                },
                topLeft = Offset(left, top),
                size = Size(sheetWidth, sheetLength),
                cornerRadius = CornerRadius(3f),
            )

            // Perforations, so the number of squares is countable at a glance.
            for (i in 1 until maxOf(hanging, 1)) {
                val y = top + i * PaperRoll.SQUARE
                drawLine(
                    color = if (isCash) BILL_INK.copy(alpha = 0.55f) else palette.porcelainShadow.toColor().copy(alpha = 0.30f),
                    start = Offset(left + 4, y),
                    end = Offset(left + sheetWidth - 4, y),
                    strokeWidth = 1f,
                )
            }

            // A hundred on every square, so what is hanging there is unmistakable.
            if (isCash) {
                val style = TextStyle(
                    color = BILL_INK,
                    // Eleven design units tall, expressed in sp for the measurer.
                    fontSize = TextUnit(11f * unitPx / density, TextUnitType.Sp),
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                )
                for (i in 0 until maxOf(hanging, 1)) {
                    val cellTop = top + i * PaperRoll.SQUARE
                    drawRoundRect(
                        color = BILL_INK.copy(alpha = 0.35f),
                        topLeft = Offset(left + 7, cellTop + 2),
                        size = Size(sheetWidth - 14, PaperRoll.SQUARE - 4),
                        cornerRadius = CornerRadius(2f),
                        style = Stroke(width = 0.8f),
                    )
                    val measured = measurer.measure("100", style)
                    // Measured in real pixels; this scope is in design units.
                    val w = measured.size.width / unitPx
                    val h = measured.size.height / unitPx
                    scale(1 / unitPx, 1 / unitPx, pivot = Offset(cx - w / 2, cellTop + (PaperRoll.SQUARE - h) / 2)) {
                        drawText(measured, topLeft = Offset(cx - w / 2, cellTop + (PaperRoll.SQUARE - h) / 2))
                    }
                }
            }

            // A torn bottom edge while it is still attached, so "not cut" reads.
            if (!isCut) {
                drawRect(
                    color = if (isTrailing) TRAILING.copy(alpha = 0.55f) else palette.porcelainShadow.toColor().copy(alpha = 0.28f),
                    topLeft = Offset(left, top + sheetLength - 1),
                    size = Size(sheetWidth, 2f),
                )
            }
        }
    }

    // The bracket it hangs off.
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(palette.chromeLight.toColor(), palette.chromeDark.toColor()),
            startY = 26f - 17f,
            endY = 26f + 17f,
        ),
        topLeft = Offset(cx - 34 - 4.5f, 26f - 17f),
        size = Size(9f, 34f),
        cornerRadius = CornerRadius(3f),
    )

    // The roll itself, with its shadow.
    val roll = Offset(cx - 27f, 32f - 27f)
    drawOval(
        color = palette.porcelainShadow.toColor().copy(alpha = 0.30f),
        topLeft = roll + Offset(0f, 3f),
        size = Size(54f, 54f),
    )
    drawOval(
        brush = Brush.linearGradient(
            listOf(Color.White, palette.porcelainDark.toColor()),
            start = roll,
            end = roll + Offset(54f, 54f),
        ),
        topLeft = roll,
        size = Size(54f, 54f),
    )
    drawOval(
        color = palette.porcelainShadow.toColor().copy(alpha = 0.25f),
        topLeft = roll,
        size = Size(54f, 54f),
        style = Stroke(width = 1f),
    )

    // The cardboard tube.
    drawOval(
        color = palette.porcelainShadow.toColor().copy(alpha = 0.35f),
        topLeft = Offset(cx - 8.5f, 32f - 8.5f),
        size = Size(17f, 17f),
    )

    // Once torn, it sits folded and ready rather than vanishing.
    if (isCut && pulled > 0) {
        var y = 74f
        repeat(minOf(pulled, 5)) {
            drawRoundRect(
                color = palette.porcelainShadow.toColor().copy(alpha = 0.25f),
                topLeft = Offset(cx - 17f, y + 1),
                size = Size(34f, 4f),
                cornerRadius = CornerRadius(1.5f),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - 17f, y),
                size = Size(34f, 4f),
                cornerRadius = CornerRadius(1.5f),
            )
            y += 6f
        }
    }
}
