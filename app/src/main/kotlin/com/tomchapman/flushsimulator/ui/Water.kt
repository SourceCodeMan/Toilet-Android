package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import com.tomchapman.flushsimulator.core.Palette
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Everything inside the rim: the dry porcelain, the pool, the vortex and the foam.
 *
 * Drawn rather than stacked, because the swirl is a spiral sampled every frame and
 * because clipping a dozen layers to the same ellipse gets expensive fast. The Swift
 * used a `Canvas` here for the same reason, so this is close to a line-for-line port.
 */
object Water {
    const val WIDTH = 178f
    const val HEIGHT = 68f
}

/** The colour everything creeps toward as the bowl is neglected. */
private val FILTH = Color(red = 0.36f, green = 0.27f, blue = 0.10f)

/**
 * @param level 0 = drained, 1 = brimming.
 * @param spin total rotation of the water so far, in degrees.
 * @param turbulence 0 = still, 1 = churning.
 * @param clock seconds — the flush clock while one runs, wall time when at rest.
 * @param grime how filthy the bowl is, 0..1.
 */
fun DrawScope.drawWater(
    topLeft: Offset,
    level: Double,
    spin: Double,
    turbulence: Double,
    clock: Double,
    palette: Palette,
    grime: Double,
) {
    translate(topLeft.x, topLeft.y) {
        val rim = Rect(0f, 0f, Water.WIDTH, Water.HEIGHT)
        val rimPath = Path().apply { addOval(rim) }

        clipPath(rimPath) {
            drawDryBowl(rim, palette)

            val pool = surfaceRect(rim, level, clock)
            if (grime > 0.01) drawStains(rim, pool, grime)
            drawPool(pool, palette, grime)

            if (turbulence > 0.01) {
                drawVortex(pool, spin, turbulence, palette)
                drawBubbles(pool, spin, turbulence, clock, palette)
            }

            drawHighlights(rim, pool, turbulence, palette)
        }
    }
}

private fun DrawScope.drawDryBowl(rim: Rect, palette: Palette) {
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(palette.porcelainMid.toColor(), palette.porcelainDark.toColor()),
            start = Offset(rim.center.x, rim.top),
            end = Offset(rim.center.x, rim.bottom),
        ),
        topLeft = rim.topLeft,
        size = rim.size,
    )
}

private fun surfaceRect(rim: Rect, level: Double, clock: Double): Rect {
    // A breath of movement even at rest, so the pool never looks like a sticker.
    val shimmer = sin(clock * 1.7) * 0.006
    val l = (level + shimmer).coerceIn(0.0, 1.0).toFloat()
    val width = rim.width * (0.46f + 0.54f * l)
    val height = rim.height * (0.34f + 0.66f * l)
    val centreY = rim.center.y + (1 - l) * rim.height * 0.18f
    return Rect(rim.center.x - width / 2, centreY - height / 2, rim.center.x + width / 2, centreY + height / 2)
}

private fun DrawScope.drawPool(pool: Rect, palette: Palette, grime: Double) {
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(palette.waterLight.toColor(), palette.waterDark.toColor()),
            start = Offset(pool.center.x, pool.top),
            end = Offset(pool.center.x, pool.bottom),
        ),
        topLeft = pool.topLeft,
        size = pool.size,
    )

    // Clouds the water rather than replacing it, so a fixture's own colour still
    // shows through a dirty bowl.
    if (grime > 0.01) {
        drawOval(
            color = FILTH.copy(alpha = (0.60 * grime).toFloat()),
            topLeft = pool.topLeft,
            size = pool.size,
        )
    }
}

/**
 * The ring at the waterline, and the streaks running down to it.
 *
 * Drawn on the dry porcelain underneath the pool, so the water covers its lower half
 * and the ring reads as sitting exactly at the surface — which is where a real one
 * forms.
 */
private fun DrawScope.drawStains(rim: Rect, pool: Rect, grime: Double) {
    val strength = grime.coerceAtMost(1.0).toFloat()

    // A broad haze across the whole basin.
    val haze = rim.deflate(rim.width * 0.06f, rim.height * 0.06f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FILTH.copy(alpha = 0.42f * strength), Color.Transparent),
            center = Offset(rim.center.x, rim.center.y + rim.height * 0.12f),
            radius = rim.width * 0.52f,
        ),
        topLeft = haze.topLeft,
        size = haze.size,
    )

    // The ring itself, thickening as things get worse.
    val ring = pool.inflate(pool.width * 0.045f, pool.height * 0.075f)
    drawOval(
        color = FILTH.copy(alpha = 0.55f * strength),
        topLeft = ring.topLeft,
        size = ring.size,
        style = Stroke(width = 1.5f + 3.0f * strength),
    )

    // Streaks down the back wall. They only show once it is genuinely neglected.
    if (strength <= 0.35f) return
    val show = (strength - 0.35f) / 0.65f
    val streaks = Path()
    for (i in 0 until 5) {
        val t = (i + 0.5f) / 5f
        val x = rim.left + rim.width * (0.18f + 0.64f * t)
        val top = rim.top + rim.height * (0.16f + 0.08f * sin(i * 2.1).toFloat())
        streaks.moveTo(x, top)
        streaks.quadraticTo(x - 3, (top + ring.top) / 2, x + 2.5f, ring.top + 2)
    }
    drawPath(
        path = streaks,
        color = FILTH.copy(alpha = 0.42f * show),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawVortex(pool: Rect, spin: Double, turbulence: Double, palette: Palette) {
    val poolPath = Path().apply { addOval(pool) }
    clipPath(poolPath) {
        val centre = pool.center
        val rx = pool.width / 2
        val ry = pool.height / 2
        val phase = spin * Math.PI / 180

        for (arm in 0 until 3) {
            val path = Path()
            val offset = phase + arm * (2 * Math.PI / 3)
            val steps = 46
            for (step in 0..steps) {
                val u = step.toDouble() / steps          // 0 at the rim, 1 at the drain
                val theta = offset + u * 3.4 * Math.PI
                val radius = 1 - u * 0.94
                val point = Offset(
                    centre.x + (cos(theta) * rx * radius).toFloat(),
                    centre.y + (sin(theta) * ry * radius).toFloat(),
                )
                if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = palette.foam.toColor().copy(alpha = (0.40 * turbulence).toFloat()),
                style = Stroke(width = 3.0f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // The hole it all disappears into.
        val coreRadius = (10 * turbulence).toFloat()
        if (coreRadius > 0.5f) {
            drawOval(
                color = palette.waterDark.toColor().copy(alpha = 0.85f),
                topLeft = Offset(centre.x - coreRadius, centre.y - coreRadius * 0.42f),
                size = Size(coreRadius * 2, coreRadius * 0.84f),
            )
        }
    }
}

private fun DrawScope.drawBubbles(
    pool: Rect,
    spin: Double,
    turbulence: Double,
    clock: Double,
    palette: Palette,
) {
    val poolPath = Path().apply { addOval(pool) }
    clipPath(poolPath) {
        val centre = pool.center
        val rx = pool.width / 2
        val ry = pool.height / 2
        val phase = spin * Math.PI / 180

        for (index in 0 until 18) {
            val seed = hash(index)
            val speed = 0.5 + seed * 0.55
            val travel = fract(clock * speed + seed)          // rim to drain, then round again
            val radius = (1 - travel) * 0.92
            val theta = phase * (0.7 + seed * 0.5) + seed * 2 * Math.PI
            val dot = ((1.1 + seed * 2.3) * (0.35 + 0.65 * turbulence)).toFloat()
            val point = Offset(
                centre.x + (cos(theta) * rx * radius).toFloat(),
                centre.y + (sin(theta) * ry * radius).toFloat(),
            )
            drawOval(
                color = palette.foam.toColor().copy(alpha = (0.8 * turbulence * (1 - travel)).toFloat()),
                topLeft = Offset(point.x - dot, point.y - dot),
                size = Size(dot * 2, dot * 2),
            )
        }
    }
}

private fun DrawScope.drawHighlights(rim: Rect, pool: Rect, turbulence: Double, palette: Palette) {
    // Foam collecting at the edge of the pool.
    drawOval(
        color = palette.foam.toColor().copy(alpha = (0.22 + 0.5 * turbulence).toFloat()),
        topLeft = pool.topLeft,
        size = pool.size,
        style = Stroke(width = 1.5f),
    )

    // A glint off the surface.
    val glint = Rect(
        pool.left + pool.width * 0.17f,
        pool.top + pool.height * 0.14f,
        pool.left + pool.width * 0.47f,
        pool.top + pool.height * 0.29f,
    )
    drawOval(color = Color.White.copy(alpha = 0.30f), topLeft = glint.topLeft, size = glint.size)

    // Shadow cast by the rim. The Swift blurred a 6pt stroke; three fading strokes
    // read the same at this size and keep the whole thing inside one Canvas.
    val shade = rim.deflate(2f, 2f)
    for (i in 0 until 3) {
        drawOval(
            color = palette.porcelainShadow.toColor().copy(alpha = 0.22f),
            topLeft = shade.topLeft - Offset(i.toFloat(), i.toFloat()),
            size = Size(shade.width + i * 2, shade.height + i * 2),
            style = Stroke(width = 6f - i),
        )
    }
}

private fun hash(index: Int): Double = fract(sin(index * 127.1 + 13.7) * 43_758.5453)

private fun fract(x: Double): Double = x - floor(x)

private fun Rect.deflate(dx: Float, dy: Float) = Rect(left + dx, top + dy, right - dx, bottom - dy)

private fun Rect.inflate(dx: Float, dy: Float) = Rect(left - dx, top - dy, right + dx, bottom + dy)
