package com.tomchapman.flushsimulator.ui

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomchapman.flushsimulator.R
import com.tomchapman.flushsimulator.core.DailyChallenge
import com.tomchapman.flushsimulator.core.DailyResult
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Upkeep
import java.text.NumberFormat

private fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/**
 * What the tank was worth, once it runs dry.
 *
 * A run needs an ending or it is just an accumulator with extra steps. This is the
 * moment the score stops moving and you decide whether to go again.
 */
@Composable
fun RunSummary(
    score: Int,
    best: Int,
    bestStreak: Int,
    palette: Palette,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBest = score >= best && score > 0

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.roomBottom.toColor().copy(alpha = 0.98f))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = if (isBest) "BEST TANK YET" else "TANK EMPTY",
            color = if (isBest) palette.accent.toColor() else palette.ink.toColor().copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = fmt(score), color = palette.ink.toColor(), fontSize = 60.sp, fontWeight = FontWeight.Black)
            Text(
                text = "POINTS",
                color = palette.ink.toColor().copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.ink.toColor().copy(alpha = 0.08f))
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tally("BEST", fmt(best), palette, Modifier.weight(1f))
            ThinDivider(palette)
            Tally("STREAK", fmt(bestStreak), palette, Modifier.weight(1f))
            ThinDivider(palette)
            Tally("FLUSHES", Upkeep.RUN_LENGTH.toString(), palette, Modifier.weight(1f))
        }

        PrimaryButton("New tank", palette, onClick = onAgain)
    }
}

/** Today's puzzle: what it asks for, how it went, and something to paste at people. */
@Composable
fun DailySheet(
    challenge: DailyChallenge,
    daily: DailyResult?,
    palette: Palette,
    onStart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.roomBottom.toColor().copy(alpha = 0.98f))
            .padding(horizontal = 26.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Daily Flush",
                color = palette.ink.toColor(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text("Done", color = palette.accent.toColor(), fontWeight = FontWeight.Bold)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "#${challenge.number}", color = palette.ink.toColor(), fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(
                text = challenge.fixture.name,
                color = palette.ink.toColor().copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        when {
            daily != null && daily.isComplete -> {
                Grid(daily, palette)
                Score(daily.score, palette)
                PrimaryButton("Share result", palette, symbol = "square.and.arrow.up") {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, daily.shareText(challenge))
                    context.startActivity(Intent.createChooser(send, null))
                }
                Note("A new one tomorrow.", palette)
            }

            daily != null -> {
                Grid(daily, palette)
                Score(daily.score, palette)
                Note(
                    "Flush ${daily.marks.size + 1} of ${DailyChallenge.FLUSH_COUNT}. Close this and pull the handle.",
                    palette,
                )
            }

            else -> {
                Setup(challenge, palette)
                PrimaryButton("Play today's", palette) {
                    onStart()
                    onClose()
                }
                Note("One attempt. Everyone gets the same bowl.", palette)
            }
        }
    }
}

/** What the day hands you, before you touch anything. */
@Composable
private fun Setup(challenge: DailyChallenge, palette: Palette) {
    val squares = "${challenge.paperTarget} square${if (challenge.paperTarget == 1) "" else "s"} exactly"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // Tinted from ink, not porcelain: a porcelain card under a dark palette's
            // light ink is unreadable. Ink-on-ink holds contrast either way.
            .background(palette.ink.toColor().copy(alpha = 0.10f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupRow("Toilet", challenge.fixture.name, "toilet.fill", palette)
        SetupRow("Paper", squares, "square.stack.3d.up.fill", palette)
        SetupRow("Bowl", "${(challenge.startingGrime * 100).toInt()}% dirty to start", "drop.triangle.fill", palette)
        SetupRow("Flushes", DailyChallenge.FLUSH_COUNT.toString(), "arrow.triangle.2.circlepath", palette)
    }
}

@Composable
private fun SetupRow(title: String, value: String, symbol: String, palette: Palette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(symbolFor(symbol), contentDescription = null, tint = palette.ink.toColor(), modifier = Modifier.size(16.dp))
        Text(title, color = palette.ink.toColor().copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f))
        Text(value, color = palette.ink.toColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

/** The run so far, as the squares that get shared. */
@Composable
private fun Grid(run: DailyResult, palette: Palette) {
    Row(
        Modifier.semantics { contentDescription = "${run.marks.size} of ${DailyChallenge.FLUSH_COUNT} flushes done" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(DailyChallenge.FLUSH_COUNT) { i ->
            val done = i < run.marks.size
            // The empty slot is the emoji small square, as on iOS: the bare U+25AB
            // glyph comes out of the text font a quarter the size of its neighbours.
            Text(
                text = if (done) run.marks[i].emoji else "▫️",
                fontSize = 30.sp,
                color = palette.ink.toColor().copy(alpha = if (done) 1f else 0.35f),
            )
        }
    }
}

@Composable
private fun Score(score: Int, palette: Palette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmt(score), color = palette.ink.toColor(), fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text(
            "POINTS",
            color = palette.ink.toColor().copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun Note(text: String, palette: Palette) {
    Text(
        text,
        color = palette.ink.toColor().copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PrimaryButton(label: String, palette: Palette, symbol: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(palette.accent.toColor())
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            Icon(symbolFor(symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).padding(end = 2.dp))
        }
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Tally(title: String, value: String, palette: Palette, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = palette.ink.toColor(), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            title,
            color = palette.ink.toColor().copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun ThinDivider(palette: Palette) {
    Box(Modifier.width(1.dp).height(30.dp).background(palette.ink.toColor().copy(alpha = 0.15f)))
}

/**
 * The payout card for Benjamin's lucky roll.
 *
 * The Easter egg was his idea, so the picture that shows up when it lands is him.
 * Drops in over the gold, holds while the celebration runs, and leaves with it.
 */
@Composable
fun CashPayout(modifier: Modifier = Modifier) {
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }
    val spring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    val scale by animateFloatAsState(if (landed) 1f else 0.7f, spring, label = "cash-scale")
    val tilt by animateFloatAsState(if (landed) -3f else 10f, spring, label = "cash-tilt")
    val alpha by animateFloatAsState(if (landed) 1f else 0f, spring, label = "cash-alpha")

    val gold = Color(red = 0.99f, green = 0.80f, blue = 0.22f)
    val ink = Color(red = 0.30f, green = 0.20f, blue = 0.01f)

    Column(
        modifier
            .width(236.dp)
            .scale(scale)
            .rotate(tilt)
            .clip(RoundedCornerShape(18.dp))
            .background(gold.copy(alpha = alpha))
            .semantics { contentDescription = "Benjamin's lucky roll. One flush in a hundred pays in hundreds." },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.benjamin),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        )
        Column(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("BENJAMIN'S LUCKY ROLL", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("one in a hundred", color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
