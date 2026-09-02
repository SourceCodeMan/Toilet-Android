package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tomchapman.flushsimulator.core.Fixture
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.Palette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The drawing, rendered to PNGs on the JVM.
 *
 * The deliverable here is whether the toilet *looks* right, which no assertion can
 * judge. These record the moments worth looking at — the surge, the drain, a
 * neglected bowl, each fixture — so the port can be reviewed as pictures and held up
 * against the iOS app side by side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToiletSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        fixture: Fixture = Fixture.Standard,
        dark: Boolean = false,
        elapsed: Double? = null,
        grime: Double = 0.0,
        drag: Double = 0.0,
        palette: Palette? = null,
        profile: FlushProfile? = null,
        celebration: Double? = null,
    ) {
        compose.setContent {
            Stage(
                palette = palette ?: fixture.palette(dark),
                profile = profile ?: fixture.profile,
                fixture = fixture,
                elapsed = elapsed,
                grime = grime,
                drag = drag,
                celebration = celebration,
            )
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun Stage(
        palette: Palette,
        profile: FlushProfile,
        fixture: Fixture,
        elapsed: Double?,
        grime: Double,
        drag: Double,
        celebration: Double?,
    ) = FlushStage(
        palette = palette,
        profile = profile,
        surface = fixture.surface,
        modifier = Modifier.fillMaxSize(),
        elapsed = elapsed,
        grime = grime,
        drag = drag,
        celebration = celebration,
    )

    // At rest

    @Test fun restLight() = shot("rest-light")

    @Test fun restDark() = shot("rest-dark", dark = true)

    @Test fun handlePushed() = shot("handle-pushed", drag = 1.0)

    // Through a flush, at the moments the Swift's own comments call out.

    @Test fun surge() = shot("flush-1-surge", elapsed = 0.55)

    @Test fun drain() = shot("flush-2-drain", elapsed = 1.10)

    @Test fun bottom() = shot("flush-3-bottom", elapsed = 1.60)

    @Test fun refill() = shot("flush-4-refill", elapsed = 2.60)

    // Upkeep

    @Test fun grimy() = shot("grime-grimy", grime = 0.70)

    @Test fun filthy() = shot("grime-filthy", grime = 0.95)

    // Gold

    @Test fun golden() = shot("golden", elapsed = 0.9, palette = Palette.golden(dark = false))

    @Test fun goldenCelebration() = shot(
        "golden-celebration",
        palette = Palette.golden(dark = false),
        celebration = 1.4,
    )

    // The catalogue

    @Test fun outhouse() = shot("fixture-outhouse", Fixture.Outhouse, elapsed = 0.8)

    @Test fun victorian() = shot("fixture-victorian", Fixture.Victorian, elapsed = 0.8)

    @Test fun chrome() = shot("fixture-chrome", Fixture.Chrome, elapsed = 0.6)

    @Test fun orbital() = shot("fixture-orbital", Fixture.Orbital, elapsed = 0.7)

    @Test fun orbitalDark() = shot("fixture-orbital-dark", Fixture.Orbital, dark = true)
}
