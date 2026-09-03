package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.tomchapman.flushsimulator.core.DailyChallenge
import com.tomchapman.flushsimulator.core.DailyMark
import com.tomchapman.flushsimulator.core.DailyResult
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.Palette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sheets and the payout card, on their own.
 *
 * They open in dialogs on the real screen, and a dialog is its own window: a capture
 * of the screen's root never sees it. So each is rendered flat against the room colour
 * it would sit over.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SheetSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val palette: Palette = Fixture.Standard.palette(false)
    private val challenge = DailyChallenge.forStamp(9_376)   // #76: the outhouse, four squares

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(
                Modifier.fillMaxSize().background(palette.roomTop.toColor()).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        // Long enough for the payout's spring to land.
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun tankEmpty() = shot("sheet-1-tank-empty") {
        RunSummary(score = 1_180, best = 1_240, bestStreak = 4, palette = palette, onAgain = {})
    }

    @Test fun bestTank() = shot("sheet-2-best-tank") {
        RunSummary(score = 1_480, best = 1_480, bestStreak = 7, palette = palette, onAgain = {})
    }

    @Test fun dailySetup() = shot("sheet-3-daily-setup") {
        DailySheet(challenge = challenge, daily = null, palette = palette, onStart = {}, onClose = {})
    }

    @Test fun dailyRunning() = shot("sheet-4-daily-running") {
        DailySheet(
            challenge = challenge,
            daily = DailyResult(9_376, listOf(DailyMark.Golden, DailyMark.Perfect), 410),
            palette = palette,
            onStart = {},
            onClose = {},
        )
    }

    @Test fun dailyDone() = shot("sheet-5-daily-done") {
        DailySheet(
            challenge = challenge,
            daily = DailyResult(
                9_376,
                listOf(DailyMark.Golden, DailyMark.Perfect, DailyMark.Good, DailyMark.Poor, DailyMark.Clogged),
                1_210,
            ),
            palette = palette,
            onStart = {},
            onClose = {},
        )
    }

    @Test fun cashPayout() = shot("sheet-6-cash-payout") { CashPayout() }
}
