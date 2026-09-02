package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole screen, assembled.
 *
 * State is seeded through the save rather than driven: the engine reads its tally,
 * grime, paper and fixture straight out of settings, so a screenshot of a well-played
 * game is a matter of writing one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The default Robolectric device is a 320x470 phone nobody has owned for a decade,
// which squeezes the stage down to nothing. This is a real one.
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScreenSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, saved: Map<String, Any> = emptyMap()) {
        // The hint pulses and the pool shimmers forever. Letting the test clock run
        // free means never reaching idle, so it is pinned and stepped by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { FlushScreen(MapSettings(saved)) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun fresh() = shot("screen-1-fresh")

    @Test fun played() = shot(
        "screen-2-played",
        mapOf(
            "totalFlushes" to 137,
            "goldenFlushes" to 6,
            "bestStreak" to 4,
            "paper" to 3,
            "equippedFixture" to "victorian",
            "grime" to 0.55,
        ),
    )

    @Test fun filthy() = shot(
        "screen-3-filthy",
        mapOf("totalFlushes" to 640, "goldenFlushes" to 31, "paper" to 5, "grime" to 0.93),
    )

    @Test
    // "+night" merges into the class qualifiers; Android insists on its own
    // canonical ordering, so appending the string by hand is rejected.
    @Config(qualifiers = "+night")
    fun dark() = shot("screen-4-dark", mapOf("totalFlushes" to 42, "goldenFlushes" to 2))
}

