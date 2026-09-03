package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Upkeep

/** A multiplier the way it reads on a chip: 1.6, not 1.60; 1, not 1.00. */
private fun payoutLabel(payout: Double): String =
    "×" + "%.2f".format(payout).trimEnd('0').trimEnd('.')

/**
 * The row of toilets you own, plus a hint of the next one.
 *
 * Locked fixtures stay visible on purpose — the point of a collection is knowing what
 * you have not got yet.
 */
@Composable
fun FixtureBar(
    fixtures: List<Fixture>,
    equipped: Fixture,
    totalFlushes: Int,
    palette: Palette,
    onPick: (Fixture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    // Otherwise installing something off the end of the row leaves the bar looking
    // like nothing is selected at all.
    LaunchedEffect(equipped) {
        val index = fixtures.indexOf(equipped)
        if (index >= 0) state.animateScrollToItem(index)
    }

    LazyRow(
        modifier.fillMaxWidth().height(38.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(fixtures) { _, fixture ->
            val locked = totalFlushes < fixture.unlockAt
            val isOn = fixture == equipped
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isOn) palette.accent.toColor() else palette.ink.toColor().copy(alpha = 0.08f))
                    .clickable(enabled = !locked) { onPick(fixture) }
                    // A locked chip shows only its unlock count, which read aloud is
                    // just a number with no idea what it belongs to.
                    .semantics {
                        contentDescription = if (locked) {
                            "${fixture.name}, locked. ${fixture.unlockAt} flushes to unlock."
                        } else {
                            "${fixture.name}, pays ${payoutLabel(fixture.payout).drop(1)} times. ${fixture.blurb}"
                        }
                        if (isOn) stateDescription = "Installed"
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tint =
                    if (isOn) Color.White
                    else palette.ink.toColor().copy(alpha = if (locked) 0.35f else 0.75f)
                Icon(
                    imageVector = symbolFor(if (locked) "lock.fill" else fixture.symbol),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (locked) fixture.unlockAt.toString() else fixture.name,
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                // The payout is the whole reason to pick one over another, so it has
                // to be on the chip rather than buried in a blurb.
                if (!locked) {
                    Text(
                        text = payoutLabel(fixture.payout),
                        color = tint.copy(alpha = if (isOn) 0.9f else 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

/**
 * The wand, and a word about what the bowl needs.
 *
 * Paper and the plunger used to live here as a stepper and a big red button. Both are
 * objects in the room now — see `PaperRoll` and `Plunger` — so what is left is the
 * wand, and a line telling you which step of a blockage you are on. Without that line
 * a blocked bowl offers no clue that the plunger on the floor is the answer.
 */
@Composable
fun UpkeepBar(
    grime: Double,
    isFlushing: Boolean,
    isClogged: Boolean,
    isPaperTrailing: Boolean,
    plunges: Int,
    palette: Palette,
    onWand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The sign gets whatever the wand leaves, and fits its type to that.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (isClogged) Instruction(isPaperTrailing, plunges)
        }
        Wand(grime, isFlushing, palette, onWand)
    }
}

/**
 * What to do next, in the order it has to happen.
 *
 * Only a sign. The plunger on the floor is the way to clear a blockage.
 */
@Composable
private fun Instruction(isPaperTrailing: Boolean, plunges: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(red = 0.86f, green = 0.34f, blue = 0.24f))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = symbolFor(if (isPaperTrailing) "scissors" else "arrow.down.circle.fill"),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        // Shrinks rather than truncates, as the Swift's minimumScaleFactor does: the
        // longer line is a shade too wide for a 393dp phone at full size, and a sign
        // that reads "Swipe acro..." is no sign at all.
        BasicText(
            text = if (isPaperTrailing) {
                "Swipe across the paper to cut it free"
            } else {
                "Drag the plunger over the bowl · $plunges/${Upkeep.PLUNGES_TO_CLEAR}"
            },
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.5.sp, maxFontSize = 12.sp, stepSize = 0.25.sp),
        )
    }
}

/** The wand, with how filthy the bowl is drawn straight into it. */
@Composable
private fun Wand(grime: Double, isFlushing: Boolean, palette: Palette, onWand: () -> Unit) {
    val live = grime > 0 && !isFlushing
    val tint = palette.ink.toColor().copy(alpha = if (grime > 0) 1f else 0.35f)
    val fill = when {
        grime >= Upkeep.GRIMY_ABOVE -> Color(red = 0.48f, green = 0.36f, blue = 0.14f)
        grime <= Upkeep.CLEAN_BELOW -> palette.accent.toColor()
        else -> Color(red = 0.66f, green = 0.55f, blue = 0.24f)
    }

    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(palette.ink.toColor().copy(alpha = 0.09f))
            .clickable(enabled = live, onClick = onWand)
            .semantics {
                contentDescription = "Potty wand. Scrubs the bowl; a clean one flushes gold more often."
                stateDescription =
                    if (grime <= 0) "Bowl is clean" else "${(grime * 100).toInt()} percent dirty"
            }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Canvas(Modifier.size(width = 16.dp, height = 22.dp).rotate(-14f)) { drawWand(tint) }
        Box(
            Modifier
                .width(46.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(palette.ink.toColor().copy(alpha = 0.14f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(grime.coerceIn(0.0, 1.0).toFloat())
                    .height(7.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(fill),
            )
        }
    }
}

/**
 * A potty wand, drawn rather than borrowed.
 *
 * Nothing in any icon set reads as a toilet wand — a paintbrush was the closest and it
 * looked like decorating. The silhouette that sells it is a long thin shaft with a
 * fat, blunt, slightly scalloped head.
 */
private fun DrawScope.drawWand(tint: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    fun capsule(w: Float, h: Float, dy: Float, colour: Color) = drawRoundRect(
        color = colour,
        topLeft = Offset(cx - w / 2, cy + dy - h / 2),
        size = Size(w, h),
        cornerRadius = CornerRadius(minOf(w, h) / 2),
    )

    capsule(2.4f, 11f, -5.5f, tint)                       // shaft
    capsule(4.2f, 3f, -8f, tint.copy(alpha = 0.55f))      // thumb grip
    capsule(5.5f, 2f, 1.2f, tint)                         // collar

    // The head: blunt and bulbous, not bristled.
    drawOval(color = tint, topLeft = Offset(cx - 6f, cy + 6.4f - 4.75f), size = Size(12f, 9.5f))
    drawOval(color = tint.copy(alpha = 0.45f), topLeft = Offset(cx - 3.5f, cy + 5f - 2f), size = Size(7f, 4f))
}
