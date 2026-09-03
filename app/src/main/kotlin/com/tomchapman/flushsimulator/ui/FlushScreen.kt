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
import com.tomchapman.flushsimulator.core.DailyChallenge
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.FlushAudio
import com.tomchapman.flushsimulator.core.FlushEngine
import com.tomchapman.flushsimulator.core.FlushState
import com.tomchapman.flushsimulator.core.Haptics
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
fun FlushScreen(
    settings: Settings,
    modifier: Modifier = Modifier,
    audio: FlushAudio = FlushAudio.None,
    haptics: Haptics = Haptics.None,
) {
    val scope = rememberCoroutineScope()
    val engine = remember(settings, audio, haptics) {
        FlushEngine(settings = settings, scope = scope, audio = audio, haptics = haptics)
    }
    val client = remember(settings) { BoardClient(settings) }

    // Rendering a voice takes a moment, so the installed one is started on before
    // anybody reaches for the handle.
    LaunchedEffect(engine) { audio.prepare(engine.state.value.fixture.profile) }

    FlushScreen(engine, modifier, client, audio)
}

@Composable
fun FlushScreen(
    engine: FlushEngine,
    modifier: Modifier = Modifier,
    client: BoardClient? = null,
    audio: FlushAudio = FlushAudio.None,
) {
    val state by engine.state.collectAsStateCompat()
    val dark = isSystemInDarkTheme()
    var confirmingReset by remember { mutableStateOf(false) }
    var showingBoard by remember { mutableStateOf(false) }
    var showingDaily by remember { mutableStateOf(false) }
    var showingRunEnd by remember { mutableStateOf(false) }
    var muted by remember(audio) { mutableStateOf(audio.isMuted) }

    // Where the toilet's feet land, in root pixels, so the floor can meet them.
    var floorY by remember { mutableStateOf<Float?>(null) }

    // Gold is an overlay on whatever is installed, not a fixture of its own.
    val palette = if (state.showsGold) Palette.golden(dark) else state.fixture.palette(dark)

    // The tank running dry is the one event that has to interrupt: there is no legal
    // move behind the summary, and only its button starts a new one.
    LaunchedEffect(state.isRunOver) {
        if (state.isRunOver) showingRunEnd = true
    }
    // Reaching for the handle on a dry tank brings the summary back, so putting it
    // away by accident is not the end of the game.
    LaunchedEffect(state.dryTankAsks) {
        if (state.dryTankAsks > 0 && state.isRunOver) showingRunEnd = true
    }

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
        Canvas(Modifier.fillMaxSize()) { drawBathroom(palette, state.fixture.surface, floorY) }

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
                isDailyDone = state.isDailyDone,
                onDaily = { showingDaily = true },
                onLeaderboard = { showingBoard = true },
                onToggleMute = {
                    muted = !muted
                    audio.isMuted = muted
                },
            )

            BathroomStage(
                state = state,
                palette = palette,
                onPull = engine::pullHandle,
                onPress = engine::handleTouched,
                onPullPaper = engine::pullPaper,
                onCutPaper = engine::cutPaper,
                onPump = engine::plunge,
                onFloorLine = { floorY = it },
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
                grime = state.grime,
                isFlushing = state.isFlushing,
                isClogged = state.isClogged,
                isPaperTrailing = state.isPaperTrailing,
                plunges = state.plunges,
                palette = palette,
                onWand = engine::useWand,
            )

            StatsCard(
                palette = palette,
                dark = dark,
                totalFlushes = state.totalFlushes,
                flushesLeft = state.flushesLeft,
                runScore = state.runScore,
                isRunOver = state.isRunOver,
                streak = state.streak,
                bestStreak = state.bestStreak,
                onTank = { showingRunEnd = true },
                onLongPress = { confirmingReset = true },
            )

            Hint(palette, hintText(state), pulse)
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

        if (state.isCashPayout) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CashPayout() }
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

    if (showingDaily) {
        Dialog(onDismissRequest = { showingDaily = false }) {
            DailySheet(
                challenge = engine.challenge,
                daily = state.daily,
                palette = palette,
                onStart = engine::startDaily,
                onClose = { showingDaily = false },
            )
        }
    }

    if (showingRunEnd) {
        // Not dismissable by tapping outside or by the back button: the tank is dry and
        // only the button starts a new one. Swiping it away was a dead end.
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            RunSummary(
                score = state.runScore,
                best = state.bestRun,
                bestStreak = state.bestStreak,
                palette = palette,
                onAgain = {
                    engine.startRun()
                    showingRunEnd = false
                },
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

/** While a daily is running the hint's job is to say where you are in it. */
internal fun hintText(state: FlushState): String {
    val run = state.daily
    if (run != null && !run.isComplete) {
        val c = state.challenge
        return "Daily #${c.number} · ${run.marks.size}/${DailyChallenge.FLUSH_COUNT} · ${c.paperTarget} squares"
    }
    return if (state.totalFlushes == 0) "Hold the handle" else "Hold, then let go in the window"
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
