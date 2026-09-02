package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.floor
import kotlin.math.sin

/** Falling gold, for the one flush in twenty that earns it. */
private const val FLAKES = 90

private val GOLD = listOf(
    Color(red = 1.00f, green = 0.85f, blue = 0.30f),
    Color(red = 0.98f, green = 0.72f, blue = 0.10f),
    Color(red = 1.00f, green = 0.95f, blue = 0.70f),
    Color(red = 0.86f, green = 0.60f, blue = 0.05f),
)

/** @param elapsed seconds since the celebration began. */
fun DrawScope.drawCelebration(elapsed: Double) {
    if (elapsed <= 0) return

    for (index in 0 until FLAKES) {
        val lane = hash(index, 1.0)
        val pace = 1.9 + hash(index, 2.0) * 1.3
        val delay = hash(index, 3.0) * 0.7
        val progress = (elapsed - delay) / pace
        if (progress <= 0 || progress >= 1) continue

        val sway = sin(elapsed * 3.1 + lane * 12) * 20
        val x = (lane * size.width + sway).toFloat()
        val y = (-24 + progress * (size.height + 48)).toFloat()
        val width = (5 + hash(index, 4.0) * 6).toFloat()
        val height = (8 + hash(index, 5.0) * 7).toFloat()
        val alpha = if (progress > 0.82) ((1 - progress) / 0.18).toFloat() else 1f

        translate(x, y) {
            rotate(degrees = (elapsed * 210 + lane * 360).toFloat(), pivot = Offset.Zero) {
                drawRoundRect(
                    color = GOLD[index % GOLD.size].copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(-width / 2, -height / 2),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(1.5f),
                )
            }
        }
    }
}

private fun hash(index: Int, salt: Double): Double {
    val x = sin(index * 127.1 + salt * 311.7) * 43_758.5453
    return x - floor(x)
}
