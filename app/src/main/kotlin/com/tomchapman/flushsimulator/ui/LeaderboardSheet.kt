package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomchapman.flushsimulator.board.BoardClient
import com.tomchapman.flushsimulator.board.BoardState
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Standings

/** Which board you are looking at. */
enum class BoardScope(val title: String) {
    Days("Your Days"),
    Global("Global"),
}

/**
 * Your best days, and everyone else's.
 *
 * A leaderboard in a one-player app has to rank you against something, so the first
 * tab ranks you against your own days. The second is the real one, and says plainly
 * when it is not there rather than spinning forever.
 */
@Composable
fun LeaderboardSheet(
    standings: Standings,
    lifetime: Int,
    palette: Palette,
    client: BoardClient?,
    nowMillis: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Which tab opens. Only ever passed by a screenshot that wants the other one.
    initialScope: BoardScope = BoardScope.Days,
) {
    var scope by remember { mutableStateOf(initialScope) }

    Column(
        modifier
            .fillMaxSize()
            .background(palette.roomBottom.toColor())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Leaderboard",
                color = palette.ink.toColor(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text("Done", color = palette.accent.toColor(), fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(palette.ink.toColor().copy(alpha = 0.08f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BoardScope.entries.forEach { option ->
                val on = option == scope
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (on) palette.porcelainLight.toColor().copy(alpha = 0.9f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { scope = option }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.title,
                        color = palette.ink.toColor().copy(alpha = if (on) 1f else 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        when (scope) {
            BoardScope.Days -> DaysBoard(standings, palette, nowMillis)
            BoardScope.Global -> GlobalBoard(client, lifetime, standings, palette)
        }
    }
}

@Composable
private fun DaysBoard(standings: Standings, palette: Palette, nowMillis: Long) {
    val board = standings.board
    if (board.isEmpty()) {
        Empty("No flushes recorded yet.", "Today is a blank slate.", palette)
        return
    }

    val today = standings.today(nowMillis)
    val rank = standings.todaysRank(nowMillis)
    val headline = when {
        today == null -> "Best days"
        rank != null -> "Best days — today is #$rank with ${today.score}"
        else -> "Best days — today has ${today.score} so far"
    }

    Text(
        text = headline,
        color = palette.ink.toColor().copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(board) { day ->
            val detail = buildList {
                add("${day.flushes} flushes")
                if (day.golden > 0) add("${day.golden} golden")
                if (day.bestStreak > 1) add("×${day.bestStreak} streak")
            }.joinToString(" · ")

            Row(
                rank = board.indexOf(day) + 1,
                name = Standings.label(day.stamp, nowMillis),
                score = day.score,
                detail = detail,
                isYou = day.stamp == Standings.stamp(nowMillis),
                palette = palette,
            )
        }
    }
}

@Composable
private fun GlobalBoard(
    client: BoardClient?,
    lifetime: Int,
    standings: Standings,
    palette: Palette,
) {
    var state by remember { mutableStateOf<BoardState>(BoardState.Loading) }
    var typed by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(client, attempt) {
        state = client?.refresh(lifetime, standings.bestDay?.score ?: 0)
            ?: BoardState.NotConfigured
    }

    when (val now = state) {
        BoardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.accent.toColor())
        }

        BoardState.NotConfigured -> Empty(
            "The global board isn't live yet.",
            "It is written and waiting on a deploy. Your days are still being recorded.",
            palette,
        )

        BoardState.NeedsName -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "The board needs something to call you.",
                color = palette.ink.toColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(24) },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    client?.playerName = typed.trim()
                    attempt++
                },
                enabled = typed.isNotBlank(),
            ) { Text("Join the board") }
        }

        is BoardState.Failed -> Empty("The board didn't answer.", now.why, palette)

        is BoardState.Ready ->
            if (now.entries.isEmpty()) {
                Empty("Nobody has flushed anything yet.", "Be the first.", palette)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(now.entries) { entry ->
                        Row(
                            rank = entry.rank,
                            name = entry.name,
                            score = entry.lifetime,
                            detail = if (entry.bestDay > 0) "best day ${entry.bestDay}" else "",
                            isYou = entry.isYou,
                            palette = palette,
                        )
                    }
                }
            }
    }
}

@Composable
private fun Row(
    rank: Int,
    name: String,
    score: Int,
    detail: String,
    isYou: Boolean,
    palette: Palette,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                palette.porcelainLight.toColor().copy(alpha = if (isYou) 0.55f else 0.25f),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString(),
            color = if (rank <= 3) palette.accent.toColor() else palette.ink.toColor().copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = palette.ink.toColor(),
                fontSize = 15.sp,
                fontWeight = if (isYou) FontWeight.Black else FontWeight.SemiBold,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = palette.ink.toColor().copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Text(
            text = score.toString(),
            color = palette.ink.toColor(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun Empty(title: String, note: String, palette: Palette) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 34.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = palette.ink.toColor(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = note,
            color = palette.ink.toColor().copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
