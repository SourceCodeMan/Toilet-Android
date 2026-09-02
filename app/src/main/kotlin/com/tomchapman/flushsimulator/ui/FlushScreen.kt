package com.tomchapman.flushsimulator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tomchapman.flushsimulator.board.BoardClient
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.FlushEngine
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.Settings
import kotlinx.coroutines.flow.StateFlow

/**
 * The whole app.
 *
 * The engine is remembered rather than held in a ViewModel: the screen is locked to
 * portrait, and everything worth keeping is already written to [Settings], so an
 * activity recreation reloads rather than loses. The one casualty is a flush that was
 * mid-swirl, which is the right thing to lose.
 */
@Composable
fun FlushScreen(settings: Settings, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val engine = remember(settings) { FlushEngine(settings = settings, scope = scope) }
    val client = remember(settings) { BoardClient(settings) }
    FlushScreen(engine, modifier, client)
}

@Composable
fun FlushScreen(
    engine: FlushEngine,
    modifier: Modifier = Modifier,
    client: BoardClient? = null,
) {
    val state by engine.state.collectAsStateCompat()
    val dark = isSystemInDarkTheme()
    var confirmingReset by remember { mutableStateOf(false) }
    var showingBoard by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    // Gold is an overlay on whatever is installed, not a fixture of its own.
    val palette = if (state.showsGold) Palette.golden(dark) else state.fixture.palette(dark)

    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hint",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawBathroom(palette, state.fixture.surface) }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                palette = palette,
                isMuted = muted,
                onLeaderboard = { showingBoard = true },
                onToggleMute = { muted = !muted },
            )

            Toilet(
                palette = palette,
                profile = state.activeProfile,
                grime = state.grime,
                flushStartMillis = state.flushStartMillis,
                onPull = engine::pullHandle,
                onPress = { },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            FixtureBar(
                fixtures = Fixture.all,
                equipped = state.fixture,
                totalFlushes = state.totalFlushes,
                palette = palette,
                onPick = engine::equip,
            )

            UpkeepBar(
                paper = state.paper,
                grime = state.grime,
                isFlushing = state.isFlushing,
                isClogged = state.isClogged,
                plunges = state.plunges,
                palette = palette,
                onPaper = engine::setPaper,
                onWand = engine::useWand,
                onPlunge = engine::plunge,
            )

            StatsCard(
                palette = palette,
                dark = dark,
                totalFlushes = state.totalFlushes,
                goldenFlushes = state.goldenFlushes,
                streak = state.streak,
                bestStreak = state.bestStreak,
                onLongPress = { confirmingReset = true },
            )

            Hint(palette, state.totalFlushes, pulse)
        }

        state.message?.let { message ->
            Box(
                Modifier.fillMaxSize().systemBarsPadding().padding(top = 74.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Toast(message.text, message.kind, palette)
            }
        }

        if (state.celebrationStartMillis != null) {
            Celebration(state.celebrationStartMillis!!, Modifier.fillMaxSize())
        }
    }

    if (showingBoard) {
        Dialog(
            onDismissRequest = { showingBoard = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LeaderboardSheet(
                standings = state.standings,
                lifetime = state.totalFlushes,
                palette = palette,
                client = client,
                nowMillis = System.currentTimeMillis(),
                onClose = { showingBoard = false },
            )
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Erase your flushing legacy?") },
            text = {
                Text("Every flush, every rank, every golden moment. Gone, like they were never here.")
            },
            confirmButton = {
                TextButton(onClick = {
                    engine.resetStats()
                    confirmingReset = false
                }) { Text("Erase It All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("Never Mind") }
            },
        )
    }
}

/** Falling gold, driven off its own frame clock. */
@Composable
private fun Celebration(startMillis: Long, modifier: Modifier = Modifier) {
    var elapsed by remember(startMillis) { mutableStateOf(0.0) }
    LaunchedEffect(startMillis) {
        val begin = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            elapsed = (androidx.compose.runtime.withFrameNanos { it } - begin) / 1_000_000_000.0
        }
    }
    Canvas(modifier) { drawCelebration(elapsed) }
}

/**
 * `collectAsState` lives in lifecycle-runtime-compose, which wants a compileSdk this
 * project cannot get yet. The flow is a `StateFlow` with a value already in hand, so
 * collecting it is three lines rather than a dependency.
 */
@Composable
private fun <T> StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> {
    val state = remember(this) { androidx.compose.runtime.mutableStateOf(value) }
    LaunchedEffect(this) { collect { state.value = it } }
    return state
}
