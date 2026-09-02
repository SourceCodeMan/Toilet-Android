package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.tomchapman.flushsimulator.core.FlushProfile
import com.tomchapman.flushsimulator.core.FlushTimeline
import com.tomchapman.flushsimulator.core.Palette
import kotlin.math.min

/**
 * The toilet, drawn at a fixed size and scaled to fit, so the proportions hold on
 * every screen without a single layout calculation.
 *
 * The Swift stacked a dozen SwiftUI shapes at absolute positions inside the same
 * 320x470 space. Those absolute positions are what makes one `Canvas` the simpler
 * target here: nothing needs laying out, and drawing it directly sidesteps the three
 * things Compose has no cheap answer for inside a view stack — continuous corner
 * radii, `.blur`, and coloured offset shadows.
 */
object Toilet {
    const val WIDTH = 320f
    const val HEIGHT = 470f

    /** Where the handle is, and how big a target it gets. Shared with the gesture. */
    val handlePivot = Offset(118f, 68f)
    val hitArea = Rect(32f, 24f, 148f, 112f)
}

/**
 * @param elapsed seconds since the flush began, or null when the bowl is at rest.
 * @param drag how far the finger has pushed the handle, before the flush takes over.
 * @param restClock wall-clock seconds, so the pool keeps its shimmer at rest.
 */
fun DrawScope.drawToilet(
    elapsed: Double?,
    palette: Palette,
    profile: FlushProfile,
    grime: Double,
    drag: Double,
    restClock: Double,
) {
    val level = elapsed?.let { FlushTimeline.level(it, profile) } ?: profile.restingLevel
    val spin = elapsed?.let { FlushTimeline.spin(it, profile) } ?: 0.0
    val churn = elapsed?.let { FlushTimeline.turbulence(it, profile) } ?: 0.0
    val shake = elapsed?.let { FlushTimeline.rumble(it, profile) } ?: 0.0
    // Once the flush owns the handle, the finger stops mattering.
    val push = elapsed?.let { FlushTimeline.handlePush(it) } ?: drag

    val s = min(size.width / Toilet.WIDTH, size.height / Toilet.HEIGHT)
    val dx = (size.width - Toilet.WIDTH * s) / 2
    val dy = (size.height - Toilet.HEIGHT * s) / 2

    translate(dx, dy) {
        scale(s, s, pivot = Offset.Zero) {
            translate(shake.toFloat(), (shake * 0.35).toFloat()) {
                drawFloorShadow(palette)
                drawCistern(palette)
                drawBowl(palette)
                drawSeat(palette)
                drawWater(
                    topLeft = Offset(160f - Water.WIDTH / 2, 218f - Water.HEIGHT / 2),
                    level = level,
                    spin = spin,
                    turbulence = churn,
                    clock = elapsed ?: restClock,
                    palette = palette,
                    grime = grime,
                )
                drawHandle(push, palette)
            }
        }
    }
}

// Porcelain

private fun porcelain(palette: Palette, bounds: Rect) = Brush.linearGradient(
    colors = listOf(
        palette.porcelainLight.toColor(),
        palette.porcelainMid.toColor(),
        palette.porcelainDark.toColor(),
    ),
    start = bounds.topLeft,
    end = bounds.bottomRight,
)

private fun chrome(palette: Palette, bounds: Rect) = Brush.linearGradient(
    colors = listOf(
        palette.chromeLight.toColor(),
        palette.chromeMid.toColor(),
        palette.chromeDark.toColor(),
    ),
    start = Offset(bounds.center.x, bounds.top),
    end = Offset(bounds.center.x, bounds.bottom),
)

/**
 * The Swift blurred this ellipse by 10. A radial gradient is a better match for a
 * shadow that is entirely fringe, and it needs no blur support to draw.
 */
private fun DrawScope.drawFloorShadow(palette: Palette) {
    val r = centred(160f, 438f, 238f, 30f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.porcelainShadow.toColor().copy(alpha = 0.30f),
                Color.Transparent,
            ),
            center = r.center,
            radius = r.width / 2,
        ),
        topLeft = r.topLeft,
        size = r.size,
    )
}

private fun DrawScope.drawCistern(palette: Palette) {
    val tank = centred(160f, 98f, 200f, 150f)
    dropShadow(palette, tank, 24f, dy = 6f, alpha = 0.32f)
    drawRoundRect(
        brush = porcelain(palette, tank),
        topLeft = tank.topLeft,
        size = tank.size,
        cornerRadius = CornerRadius(24f),
    )
    drawRoundRect(
        color = palette.porcelainLight.toColor().copy(alpha = 0.85f),
        topLeft = tank.topLeft,
        size = tank.size,
        cornerRadius = CornerRadius(24f),
        style = Stroke(width = 1.5f),
    )

    // A soft glare down one side, which is most of what sells "glazed". Blurred in
    // the Swift; here the softness is in the gradient instead.
    val glare = centred(228f, 100f, 30f, 104f)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                palette.porcelainLight.toColor().copy(alpha = 0.55f),
                Color.Transparent,
            ),
            startX = glare.left,
            endX = glare.right,
        ),
        topLeft = glare.topLeft,
        size = glare.size,
        cornerRadius = CornerRadius(8f),
    )

    val lid = centred(160f, 30f, 218f, 26f)
    dropShadow(palette, lid, 12f, dy = 3f, alpha = 0.30f)
    drawRoundRect(
        brush = porcelain(palette, lid),
        topLeft = lid.topLeft,
        size = lid.size,
        cornerRadius = CornerRadius(12f),
    )
    drawRoundRect(
        color = palette.porcelainLight.toColor().copy(alpha = 0.9f),
        topLeft = lid.topLeft,
        size = lid.size,
        cornerRadius = CornerRadius(12f),
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawBowl(palette: Palette) {
    val bowl = centred(160f, 297f, 216f, 178f)
    val path = bowlPath(bowl)

    translate(0f, 6f) {
        drawPath(path, color = palette.porcelainShadow.toColor().copy(alpha = 0.28f))
    }
    drawPath(path, brush = porcelain(palette, bowl))

    // The foot it stands on.
    val foot = centred(160f, 398f, 174f, 28f)
    dropShadow(palette, foot, 12f, dy = 3f, alpha = 0.25f)
    drawRoundRect(
        brush = porcelain(palette, foot),
        topLeft = foot.topLeft,
        size = foot.size,
        cornerRadius = CornerRadius(12f),
    )
    drawRoundRect(
        color = palette.porcelainLight.toColor().copy(alpha = 0.7f),
        topLeft = foot.topLeft,
        size = foot.size,
        cornerRadius = CornerRadius(12f),
        style = Stroke(width = 1.2f),
    )
}

private fun DrawScope.drawSeat(palette: Palette) {
    val seat = centred(160f, 214f, 224f, 102f)
    translate(0f, 5f) {
        drawOval(
            color = palette.porcelainShadow.toColor().copy(alpha = 0.38f),
            topLeft = seat.topLeft,
            size = seat.size,
        )
    }
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(palette.porcelainLight.toColor(), palette.porcelainMid.toColor()),
            start = seat.topLeft,
            end = seat.bottomRight,
        ),
        topLeft = seat.topLeft,
        size = seat.size,
    )
    drawOval(
        color = palette.porcelainLight.toColor(),
        topLeft = seat.topLeft,
        size = seat.size,
        style = Stroke(width = 1.5f),
    )
}

/** The only control in the app. */
private fun DrawScope.drawHandle(push: Double, palette: Palette) {
    val lever = centred(89f, 68f, 58f, 15f)

    // Rotates about its right-hand end, where the pivot sits.
    rotate(degrees = (10 - 36 * push).toFloat(), pivot = Toilet.handlePivot) {
        translate(-1f, 3f) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = lever.topLeft,
                size = lever.size,
                cornerRadius = CornerRadius(lever.height / 2),
            )
        }
        drawRoundRect(
            brush = chrome(palette, lever),
            topLeft = lever.topLeft,
            size = lever.size,
            cornerRadius = CornerRadius(lever.height / 2),
        )
        drawRoundRect(
            color = palette.chromeDark.toColor().copy(alpha = 0.35f),
            topLeft = lever.topLeft,
            size = lever.size,
            cornerRadius = CornerRadius(lever.height / 2),
            style = Stroke(width = 1f),
        )
    }

    val boss = centred(118f, 68f, 26f, 26f)
    translate(0f, 2f) {
        drawOval(color = Color.Black.copy(alpha = 0.20f), topLeft = boss.topLeft, size = boss.size)
    }
    drawOval(brush = chrome(palette, boss), topLeft = boss.topLeft, size = boss.size)
    drawOval(
        color = palette.chromeDark.toColor().copy(alpha = 0.4f),
        topLeft = boss.topLeft,
        size = boss.size,
        style = Stroke(width = 1f),
    )

    val screw = centred(118f, 68f, 6f, 6f)
    drawOval(
        color = palette.chromeDark.toColor().copy(alpha = 0.45f),
        topLeft = screw.topLeft,
        size = screw.size,
    )
}

// Shapes

/**
 * The body of the bowl: a wide rim tucking in to a narrow pedestal. The top edge
 * bulges up because the seat sits over it.
 */
private fun bowlPath(rect: Rect): Path {
    val w = rect.width
    val h = rect.height
    return Path().apply {
        moveTo(0f, h * 0.10f)
        cubicTo(w * 0.01f, h * 0.62f, w * 0.14f, h * 0.90f, w * 0.23f, h)
        lineTo(w * 0.77f, h)
        cubicTo(w * 0.86f, h * 0.90f, w * 0.99f, h * 0.62f, w, h * 0.10f)
        quadraticTo(w * 0.5f, -h * 0.14f, 0f, h * 0.10f)
        close()
        translate(Offset(rect.left, rect.top))
    }
}

/**
 * A rounded-rect drop shadow, offset rather than blurred.
 *
 * These sit behind opaque porcelain and only the fringe is ever visible, so a single
 * offset copy reads the same as SwiftUI's blurred one at these radii.
 */
private fun DrawScope.dropShadow(
    palette: Palette,
    bounds: Rect,
    corner: Float,
    dy: Float,
    alpha: Float,
) {
    translate(0f, dy) {
        drawRoundRect(
            color = palette.porcelainShadow.toColor().copy(alpha = alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(corner),
        )
    }
}

/** SwiftUI's `.position` centres a view on the point; this is the same idea. */
private fun centred(x: Float, y: Float, width: Float, height: Float) =
    Rect(Offset(x - width / 2, y - height / 2), Size(width, height))
