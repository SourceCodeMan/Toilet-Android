package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.RoomSurface

/**
 * The room, the fixture and whatever is falling out of the sky, at a fixed moment.
 *
 * The real screen composes these itself, because the background is full-bleed while
 * the toilet lives in a layout slot, and it runs its own clocks. This is the still
 * version, and it exists only for the screenshots: it takes the flush clock as a
 * number rather than running one, so a render can ask for exactly 0.55 seconds in.
 */
@Composable
fun FlushStage(
    palette: Palette,
    profile: FlushProfile,
    surface: RoomSurface,
    modifier: Modifier = Modifier,
    elapsed: Double? = null,
    grime: Double = 0.0,
    drag: Double = 0.0,
    restClock: Double = 0.0,
    celebration: Double? = null,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawBathroom(palette, surface)
            drawToilet(
                elapsed = elapsed,
                palette = palette,
                profile = profile,
                grime = grime,
                drag = drag,
                restClock = restClock,
            )
            if (celebration != null) drawCelebration(celebration)
        }
    }
}
