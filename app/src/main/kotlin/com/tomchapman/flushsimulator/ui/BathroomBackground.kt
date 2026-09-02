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
import com.tomchapman.flushsimulator.core.Palette
import com.tomchapman.flushsimulator.core.RoomSurface

/**
 * The room the toilet is standing in.
 *
 * A wall, a floor, and a horizon between them, so the fixture reads as sitting on
 * something rather than hovering against a flat backdrop. The floor recedes to a
 * vanishing point directly behind the toilet, which is what sells the depth: the
 * shadow alone was never going to.
 *
 * Every fixture brings its own surface, drawn in that fixture's own palette, so the
 * outhouse gets boards and the Victorian gets gilt without either needing a colour of
 * its own.
 */

/**
 * Where the floor meets the wall, as a fraction of height. Tuned against the toilet's
 * base rather than the middle of the screen.
 */
private const val HORIZON = 0.605f

fun DrawScope.drawBathroom(palette: Palette, surface: RoomSurface) {
    val y = size.height * HORIZON

    // Wall, lit from above.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.roomTop.toColor(), palette.roomBottom.toColor()),
            start = Offset.Zero,
            end = Offset(0f, y),
        ),
        size = Size(size.width, y),
    )

    // Floor: the room's own colour, shaded down where it meets the wall so the corner
    // reads as a corner.
    val floorTop = Offset(0f, y)
    val floorSize = Size(size.width, size.height - y)
    drawRect(color = palette.roomBottom.toColor(), topLeft = floorTop, size = floorSize)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.04f)),
            start = Offset(0f, y),
            end = Offset(0f, size.height),
        ),
        topLeft = floorTop,
        size = floorSize,
    )

    drawWall(palette, surface, y)
    drawFloor(palette, surface, y)

    // Fade the boards out as they come toward the viewer. Perspective loses contrast
    // with proximity anyway, and the controls sit down there.
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.42f to palette.roomBottom.toColor().copy(alpha = 0.55f),
                1f to palette.roomBottom.toColor().copy(alpha = 0.90f),
            ),
            start = Offset(0f, y),
            end = Offset(0f, size.height),
        ),
        topLeft = floorTop,
        size = floorSize,
    )

    // The skirting board, and the shadow the wall casts onto the floor.
    drawLine(
        color = palette.porcelainShadow.toColor().copy(alpha = 0.32f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 2f,
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.porcelainShadow.toColor().copy(alpha = 0.22f), Color.Transparent),
            start = Offset(0f, y),
            end = Offset(0f, y + 26),
        ),
        topLeft = floorTop,
        size = Size(size.width, 26f),
    )
}

// Walls

private fun DrawScope.drawWall(palette: Palette, surface: RoomSurface, horizonY: Float) {
    when (surface) {
        RoomSurface.Tile -> tiledWall(palette, horizonY)
        RoomSurface.Planks -> plankWall(horizonY)
        RoomSurface.Ornate -> ornateWall(palette, horizonY)
        RoomSurface.Panels -> panelWall(palette, horizonY)
        RoomSurface.Bulkhead -> bulkheadWall(palette, horizonY)
    }
}

/** Square tiles and grout. The original. */
private fun DrawScope.tiledWall(palette: Palette, horizonY: Float) {
    val spacing = 46f
    val grout = palette.tile.toColor()
    var y = 0f
    while (y <= horizonY) {
        drawLine(grout, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += spacing
    }
    var x = 0f
    while (x <= size.width) {
        drawLine(grout, Offset(x, 0f), Offset(x, horizonY), strokeWidth = 1.5f)
        x += spacing
    }
}

/** Upright boards with a knot or two. Gaps between them, because it is an outhouse. */
private fun DrawScope.plankWall(horizonY: Float) {
    val width = 38f
    var x = 0f
    var board = 0
    while (x <= size.width) {
        // Alternate boards sit very slightly proud of their neighbours.
        val tone = if (board % 2 == 0) 0.05f else 0.10f
        drawRect(
            color = Color.Black.copy(alpha = tone),
            topLeft = Offset(x, 0f),
            size = Size(width - 3, horizonY),
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.26f),
            start = Offset(x + width - 3, 0f),
            end = Offset(x + width - 3, horizonY),
            strokeWidth = 2.5f,
        )

        // Grain: a couple of long, lazy arcs per board.
        val grain = Path()
        for (k in 1..2) {
            val gy = horizonY * (0.22f * k + (board % 3) * 0.11f)
            grain.moveTo(x + 3, gy)
            grain.quadraticTo(x + width / 2, gy - 6, x + width - 7, gy + 5)
        }
        drawPath(grain, color = Color.Black.copy(alpha = 0.13f), style = Stroke(width = 1.2f))

        x += width
        board += 1
    }
}

/** Panelling below a dado rail, gilt stripes above it. */
private fun DrawScope.ornateWall(palette: Palette, horizonY: Float) {
    val dado = horizonY * 0.58f
    val gilt = palette.chromeMid.toColor().copy(alpha = 0.55f)

    var x = 22f
    while (x <= size.width) {
        drawLine(gilt.copy(alpha = 0.30f), Offset(x, 0f), Offset(x, dado), strokeWidth = 1.2f)
        x += 54f
    }

    // The rail itself, in two tones so it reads as moulding.
    drawLine(gilt, Offset(0f, dado), Offset(size.width, dado), strokeWidth = 3f)
    drawLine(
        color = palette.chromeLight.toColor().copy(alpha = 0.5f),
        start = Offset(0f, dado - 3),
        end = Offset(size.width, dado - 3),
        strokeWidth = 1f,
    )

    // Raised panels underneath.
    val panelW = 86f
    var px = 10f
    while (px + panelW <= size.width) {
        val r = Rect(px, dado + 16, px + panelW, horizonY - 14)
        drawRoundRect(
            color = gilt.copy(alpha = 0.45f),
            topLeft = r.topLeft,
            size = r.size,
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 1.6f),
        )
        val inner = Rect(r.left + 7, r.top + 7, r.right - 7, r.bottom - 7)
        drawRoundRect(
            color = gilt.copy(alpha = 0.22f),
            topLeft = inner.topLeft,
            size = inner.size,
            cornerRadius = CornerRadius(3f),
            style = Stroke(width = 1f),
        )
        px += panelW + 12
    }
}

/** Wide brushed sheets with recessed seams and a rivet line. */
private fun DrawScope.panelWall(palette: Palette, horizonY: Float) {
    val band = horizonY / 3
    for (i in 1..3) {
        val y = band * i
        drawLine(
            color = palette.chromeDark.toColor().copy(alpha = 0.30f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2f,
        )
        drawLine(
            color = palette.chromeLight.toColor().copy(alpha = 0.35f),
            start = Offset(0f, y + 2),
            end = Offset(size.width, y + 2),
            strokeWidth = 1f,
        )
    }

    // Brushing.
    var y = 6f
    while (y <= horizonY) {
        drawLine(
            color = Color.White.copy(alpha = 0.035f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.7f,
        )
        y += 5f
    }

    // Rivets along the top seam.
    var rx = 18f
    while (rx <= size.width) {
        drawOval(
            color = palette.chromeDark.toColor().copy(alpha = 0.35f),
            topLeft = Offset(rx, band - 3),
            size = Size(4f, 4f),
        )
        rx += 30f
    }
}

/** Ribbed hull plating with a lit strip running along it. */
private fun DrawScope.bulkheadWall(palette: Palette, horizonY: Float) {
    var x = 0f
    while (x <= size.width) {
        drawRect(
            color = Color.White.copy(alpha = 0.045f),
            topLeft = Offset(x, 0f),
            size = Size(3f, horizonY),
        )
        x += 34f
    }

    var y = 58f
    while (y <= horizonY) {
        drawLine(
            color = Color.White.copy(alpha = 0.05f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += 58f
    }

    // The strip light, and its bloom.
    val lit = horizonY * 0.30f
    drawRect(
        color = palette.accent.toColor().copy(alpha = 0.75f),
        topLeft = Offset(0f, lit),
        size = Size(size.width, 2f),
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                palette.accent.toColor().copy(alpha = 0.16f),
                Color.Transparent,
            ),
            start = Offset(0f, lit - 16),
            end = Offset(0f, lit + 18),
        ),
        topLeft = Offset(0f, lit - 16),
        size = Size(size.width, 34f),
    )
}

// Floor

/**
 * Lines running away to a vanishing point behind the toilet, plus courses that bunch
 * up as they approach the horizon. That pairing is the whole illusion.
 */
private fun DrawScope.drawFloor(palette: Palette, surface: RoomSurface, horizonY: Float) {
    val vanishing = Offset(size.width / 2, horizonY)
    val depth = size.height - horizonY
    val ink = when (surface) {
        RoomSurface.Tile -> palette.tile.toColor()
        RoomSurface.Planks -> Color.Black.copy(alpha = 0.20f)
        RoomSurface.Ornate -> palette.chromeMid.toColor().copy(alpha = 0.30f)
        RoomSurface.Panels -> palette.chromeDark.toColor().copy(alpha = 0.22f)
        RoomSurface.Bulkhead -> palette.accent.toColor().copy(alpha = 0.16f)
    }

    for (i in -7..7) {
        if (i == 0) continue
        // Spread widens off-screen so the outermost lines still leave the frame.
        drawLine(
            color = ink,
            start = vanishing,
            end = Offset(size.width / 2 + i * size.width * 0.19f, size.height),
            strokeWidth = 1.2f,
        )
    }

    // Courses. Squared spacing bunches them toward the horizon, which is what
    // foreshortening actually looks like.
    var t = 0.08f
    while (t <= 1.0f) {
        val y = horizonY + depth * t * t
        drawLine(color = ink, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.2f)
        t += 0.13f
    }
}
