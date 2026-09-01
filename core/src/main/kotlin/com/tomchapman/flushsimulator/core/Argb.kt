package com.tomchapman.flushsimulator.core

import kotlin.math.roundToInt

/**
 * A colour, as packed 8-bit ARGB.
 *
 * The core deliberately does not depend on Compose — or on anything Android — so
 * that every rule in it can be unit-tested on a plain JVM. A colour is a value, not
 * a piece of UI, so it is carried as one here and turned into a `Color` at the point
 * it is actually drawn: `Color(palette.waterDark.value)`.
 *
 * Components are sRGB in 0..1, matching the `Color(red:green:blue:)` the Swift used,
 * so the numbers copy across unchanged.
 */
@JvmInline
value class Argb(val value: Int) {

    val alpha: Int get() = (value ushr 24) and 0xFF
    val red: Int get() = (value ushr 16) and 0xFF
    val green: Int get() = (value ushr 8) and 0xFF
    val blue: Int get() = value and 0xFF

    /** The same colour at a different alpha, as SwiftUI's `.opacity` does. */
    fun opacity(a: Double): Argb {
        val alphaByte = (a.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        return Argb((value and 0x00FFFFFF) or (alphaByte shl 24))
    }

    companion object {
        val White = rgb(1.0, 1.0, 1.0)
        val Black = rgb(0.0, 0.0, 0.0)
    }
}

/** An opaque colour from sRGB components in 0..1. */
fun rgb(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Argb {
    fun byte(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    return Argb(
        (byte(alpha) shl 24) or (byte(red) shl 16) or (byte(green) shl 8) or byte(blue)
    )
}
