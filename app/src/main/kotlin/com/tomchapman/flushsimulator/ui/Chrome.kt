package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomchapman.flushsimulator.core.FlushState
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Rank

/** Title, the leaderboard, and the mute button. */
@Composable
fun Header(
    palette: Palette,
    isMuted: Boolean,
    onLeaderboard: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "FLUSH SIMULATOR",
                color = palette.ink.toColor(),
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "2026 Deluxe Porcelain Edition",
                color = palette.ink.toColor().copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        RoundButton("list.number", "Leaderboard", palette, onLeaderboard)
        RoundButton(
            symbol = if (isMuted) "speaker.slash.fill" else "speaker.wave.2.fill",
            label = if (isMuted) "Turn sound on" else "Turn sound off",
            palette = palette,
            onClick = onToggleMute,
        )
    }
}

@Composable
private fun RoundButton(symbol: String, label: String, palette: Palette, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.porcelainLight.toColor().copy(alpha = 0.55f))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = symbolFor(symbol),
            contentDescription = label,
            tint = palette.ink.toColor(),
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * Lifetime, gold, streak, and an unearned title.
 *
 * Press and hold to wipe your record, if you can bring yourself to.
 */
@Composable
fun StatsCard(
    palette: Palette,
    dark: Boolean,
    totalFlushes: Int,
    goldenFlushes: Int,
    streak: Int,
    bestStreak: Int,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rank = Rank.current(totalFlushes)
    val next = Rank.next(totalFlushes)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.porcelainLight.toColor().copy(alpha = if (dark) 0.14f else 0.6f))
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat("LIFETIME FLUSHES", totalFlushes.toString(), palette, Modifier.weight(1f))
            Divider(palette)
            Stat("GOLDEN", goldenFlushes.toString(), palette, Modifier.weight(1f))
            Divider(palette)
            // What you are on while a run is alive, and what you managed once it is over.
            Stat(
                title = if (streak > 0) "STREAK" else "BEST STREAK",
                value = (if (streak > 0) streak else bestStreak).toString(),
                palette = palette,
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = symbolFor(rank.symbol),
                    contentDescription = null,
                    tint = palette.ink.toColor(),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = rank.title,
                    color = palette.ink.toColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                Text(
                    text = if (next != null) "${next.threshold - totalFlushes} to go" else "MAXED OUT",
                    color = palette.ink.toColor().copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (next != null) FontWeight.Medium else FontWeight.Black,
                )
            }
            LinearProgressIndicator(
                progress = { Rank.progress(totalFlushes).toFloat() },
                color = palette.accent.toColor(),
                trackColor = palette.ink.toColor().copy(alpha = 0.15f),
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
    }
}

@Composable
private fun Stat(title: String, value: String, palette: Palette, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = palette.ink.toColor(),
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = title,
            color = palette.ink.toColor().copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Divider(palette: Palette) {
    Box(
        Modifier
            .width(1.dp)
            .height(32.dp)
            .background(palette.ink.toColor().copy(alpha = 0.15f)),
    )
}

/** A tap grades as a half flush, so the hint has to say hold. */
@Composable
fun Hint(palette: Palette, totalFlushes: Int, pulse: Float, modifier: Modifier = Modifier) {
    Text(
        text = if (totalFlushes == 0) "Hold the handle" else "Hold, then let go in the window",
        color = palette.ink.toColor().copy(alpha = pulse),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/** The app's running commentary. */
@Composable
fun Toast(
    text: String,
    kind: FlushState.Message.Kind,
    palette: Palette,
    modifier: Modifier = Modifier,
) {
    val background = when (kind) {
        FlushState.Message.Kind.Golden -> Color(red = 0.99f, green = 0.80f, blue = 0.22f)
        FlushState.Message.Kind.Unlock -> Color(red = 0.22f, green = 0.62f, blue = 0.38f)
        FlushState.Message.Kind.Milestone -> palette.accent.toColor()
        FlushState.Message.Kind.Busy -> palette.porcelainShadow.toColor().copy(alpha = 0.85f)
        FlushState.Message.Kind.Quip -> palette.ink.toColor().copy(alpha = 0.88f)
    }
    val foreground =
        if (kind == FlushState.Message.Kind.Golden) Color(red = 0.30f, green = 0.20f, blue = 0.01f) else Color.White
    val symbol = when (kind) {
        FlushState.Message.Kind.Golden -> "sparkles"
        FlushState.Message.Kind.Unlock -> "lock.open.fill"
        FlushState.Message.Kind.Milestone -> "flag.checkered"
        FlushState.Message.Kind.Busy -> "hourglass"
        FlushState.Message.Kind.Quip -> null
    }

    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (symbol != null) {
            Icon(
                imageVector = symbolFor(symbol),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = text,
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
