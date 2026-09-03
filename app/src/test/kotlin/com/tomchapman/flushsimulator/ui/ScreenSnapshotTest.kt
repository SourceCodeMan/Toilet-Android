package com.tomchapman.flushsimulator.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.tomchapman.flushsimulator.core.Clock
import com.tomchapman.flushsimulator.core.FlushEngine
import com.tomchapman.flushsimulator.core.FlushGrade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.random.Random

/**
 * The whole screen, assembled.
 *
 * State is seeded through the save rather than driven: the engine reads its tally,
 * grime and fixture straight out of settings, so a screenshot of a well-played game
 * is a matter of writing one. The blockage is the exception — nothing about it is
 * saved — so that one is played into being.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The default Robolectric device is a 320x470 phone nobody has owned for a decade,
// which squeezes the stage down to nothing. This is a real one.
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScreenSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private var scope: CoroutineScope? = null

    @After
    fun tearDown() {
        scope?.cancel()
        scope = null
    }

    /**
     * Pinned to noon on 2026-09-03, so the daily in every shot is #76 — the outhouse,
     * four squares — rather than whatever today happens to be.
     */
    private val noon = 1_788_436_800_000L

    private fun engine(saved: Map<String, Any>, seed: Int = 1): FlushEngine {
        val own = CoroutineScope(Dispatchers.Main)
        scope = own
        return FlushEngine(
            settings = MapSettings(saved),
            scope = own,
            clock = Clock { noon },
            random = Random(seed),
        )
    }

    private fun show(engine: FlushEngine) {
        // The hint pulses and the pool shimmers forever. Letting the test clock run
        // free means never reaching idle, so it is pinned and stepped by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { FlushScreen(engine) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun shot(name: String, saved: Map<String, Any> = emptyMap()) {
        show(engine(saved))
        capture(name)
    }

    /** Moves both clocks, as the engine settles on the looper and Compose on its own. */
    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.mainClock.advanceTimeBy(millis)
    }

    @Test fun fresh() = shot("screen-1-fresh")

    @Test fun played() = shot(
        "screen-2-played",
        mapOf(
            "totalFlushes" to 137,
            "goldenFlushes" to 6,
            "bestStreak" to 4,
            "bestRun" to 1_240,
            "equippedFixture" to "victorian",
            "grime" to 0.55,
        ),
    )

    @Test fun filthy() = shot(
        "screen-3-filthy",
        mapOf("totalFlushes" to 640, "goldenFlushes" to 31, "grime" to 0.93),
    )

    @Test
    // "+night" merges into the class qualifiers; Android insists on its own
    // canonical ordering, so appending the string by hand is rejected.
    @Config(qualifiers = "+night")
    fun dark() = shot("screen-4-dark", mapOf("totalFlushes" to 42, "goldenFlushes" to 2))

    /** Two flushes into today's daily: the hint reads the progress, the header still asks. */
    @Test fun dailyRunning() = shot(
        "screen-5-daily-running",
        mapOf("totalFlushes" to 88, "dailyResult" to "9376,410,Golden;Perfect"),
    )

    /** Today's daily finished: the calendar becomes a seal. */
    @Test fun dailyDone() = shot(
        "screen-6-daily-done",
        mapOf("totalFlushes" to 93, "dailyResult" to "9376,1210,Golden;Perfect;Good;Poor;Clogged"),
    )

    /** Some paper pulled and torn, ready by the roll. */
    @Test fun paperReady() {
        val engine = engine(mapOf("totalFlushes" to 12))
        engine.pullPaper(3)
        engine.cutPaper()
        show(engine)
        capture("screen-7-paper-ready")
    }

    /**
     * The whole roll went in. A sheet that is never torn drags the roll into the bowl,
     * which blocks it every time, so this needs no luck — only that the sheet was not
     * the one-in-a-hundred, which the seed settles.
     */
    @Test fun runaway() {
        val engine = engine(mapOf("totalFlushes" to 30))
        engine.pullPaper(4)
        assertFalse(engine.state.value.isCashRoll)
        engine.pullHandle(FlushGrade.Perfect)
        show(engine)
        advance((engine.state.value.activeProfile.duration * 1_000).toLong() + 1)
        compose.mainClock.advanceTimeByFrame()
        assertTrue(engine.state.value.isClogged)
        assertTrue(engine.state.value.isPaperTrailing)
        capture("screen-8-runaway")

        // Cut free: now it is an ordinary blockage, and the plunger is the answer.
        engine.cutPaper()
        compose.mainClock.advanceTimeByFrame()
        capture("screen-9-clogged")
    }
}
