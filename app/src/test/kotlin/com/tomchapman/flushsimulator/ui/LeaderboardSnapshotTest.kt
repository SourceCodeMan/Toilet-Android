package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.tomchapman.flushsimulator.board.BoardClient
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.Standings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/** The leaderboard, in the states it actually has. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LeaderboardSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val utc: ZoneId = ZoneId.of("UTC")
    private val noon = 1_788_264_000_000L
    private val day = 86_400_000L

    /** A fortnight of increasingly regrettable days. */
    private fun history(): Standings {
        var s = Standings()
        val flushes = listOf(3, 18, 7, 41, 12, 26, 9, 33, 5, 21, 15, 4)
        flushes.forEachIndexed { i, count ->
            repeat(count) {
                s = s.record(
                    golden = it % 7 == 0,
                    streak = it % 5,
                    points = 100 + (it % 4) * 40,
                    atMillis = noon - (flushes.size - i) * day,
                    zone = utc,
                )
            }
        }
        // And today, mid-run.
        repeat(11) {
            s = s.record(golden = it == 3, streak = it % 3, points = 140, atMillis = noon, zone = utc)
        }
        return s
    }

    private fun show(
        standings: Standings,
        client: BoardClient?,
        scope: BoardScope = BoardScope.Days,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LeaderboardSheet(
                standings = standings,
                lifetime = 205,
                palette = Fixture.Standard.palette(false),
                client = client,
                nowMillis = noon,
                onClose = {},
                initialScope = scope,
            )
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun capture(name: String) {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun days() {
        show(history(), client = null)
        capture("board-1-days")
    }

    @Test
    fun noDaysYet() {
        show(Standings(), client = null)
        capture("board-2-empty")
    }

    /** What the Global tab shows until `board/` is deployed. */
    @Test
    fun globalNotLive() {
        show(history(), client = null, scope = BoardScope.Global)
        capture("board-3-global-not-live")
    }
}
