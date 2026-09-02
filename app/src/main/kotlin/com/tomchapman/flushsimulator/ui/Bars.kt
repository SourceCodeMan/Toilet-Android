package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Upkeep

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
            }
        }
    }
}

/**
 * Paper going in, filth coming out — and the plunger, when it all goes wrong.
 *
 * One strip that swaps its whole contents while the bowl is blocked, because when it
 * is blocked there is exactly one thing worth doing.
 */
@Composable
fun UpkeepBar(
    paper: Int,
    grime: Double,
    isFlushing: Boolean,
    isClogged: Boolean,
    plunges: Int,
    palette: Palette,
    onPaper: (Int) -> Unit,
    onWand: () -> Unit,
    onPlunge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        if (isClogged) {
            Plunger(plunges, onPlunge)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PaperPicker(paper, palette, onPaper)
                Box(Modifier.weight(1f))
                Wand(grime, isFlushing, palette, onWand)
            }
        }
    }
}

@Composable
private fun Plunger(plunges: Int, onPlunge: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(red = 0.86f, green = 0.34f, blue = 0.24f))
            .clickable(onClick = onPlunge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = symbolFor("arrow.down.circle.fill"),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = "PLUNGE",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 9.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(Upkeep.PLUNGES_TO_CLEAR) { i ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (i < plunges) Color.White else Color.White.copy(alpha = 0.32f)),
                )
            }
        }
    }
}

@Composable
private fun PaperPicker(paper: Int, palette: Palette, onPaper: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("Remove", palette, enabled = paper > Upkeep.PAPER_RANGE.first) { onPaper(paper - 1) }
        Column(
            Modifier.width(58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = paper.toString(),
                color = palette.ink.toColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (paper == 1) "SQUARE" else "SQUARES",
                color = palette.ink.toColor().copy(alpha = 0.55f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        StepButton("Add", palette, enabled = paper < Upkeep.PAPER_RANGE.last) { onPaper(paper + 1) }
    }
}

@Composable
private fun StepButton(label: String, palette: Palette, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(palette.ink.toColor().copy(alpha = 0.09f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A plain glyph rather than an icon: a plus and a minus are two strokes.
        val tint = palette.ink.toColor().copy(alpha = if (enabled) 0.75f else 0.3f)
        Text(
            text = if (label == "Add") "+" else "−",
            color = tint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
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
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 16.dp, height = 22.dp).rotate(-14f)) {
            drawWand(tint)
        }
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
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(w, h) / 2),
    )

    capsule(2.4f, 11f, -5.5f, tint)                       // shaft
    capsule(4.2f, 3f, -8f, tint.copy(alpha = 0.55f))      // thumb grip
    capsule(5.5f, 2f, 1.2f, tint)                         // collar

    // The head: blunt and bulbous, not bristled.
    drawOval(
        color = tint,
        topLeft = Offset(cx - 6f, cy + 6.4f - 4.75f),
        size = Size(12f, 9.5f),
    )
    drawOval(
        color = tint.copy(alpha = 0.45f),
        topLeft = Offset(cx - 3.5f, cy + 5f - 2f),
        size = Size(7f, 4f),
    )
}
