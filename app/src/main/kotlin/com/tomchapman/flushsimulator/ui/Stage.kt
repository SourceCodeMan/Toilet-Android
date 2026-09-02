package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.RoomSurface

/**
 * The room, the fixture and whatever is falling out of the sky, in one Canvas.
 *
 * One surface rather than three stacked ones: they share a coordinate space, none of
 * them takes input, and a single pass is what keeps the flush cheap at sixty frames a
 * second.
 *
 * @param elapsed seconds since the flush began, or null when the bowl is at rest.
 * @param restClock wall-clock seconds, so the pool keeps its shimmer at rest.
 * @param celebration seconds since the gold started falling, or null.
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
    Canvas(modifier) {
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
