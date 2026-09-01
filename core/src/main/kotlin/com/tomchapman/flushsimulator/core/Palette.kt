package com.tomchapman.flushsimulator.core

/**
 * Every colour in the app, in one value.
 *
 * A golden flush swaps the whole thing out rather than tinting things one by one,
 * which is why this is a value passed down the tree instead of a pile of constants.
 *
 * Each palette is a function of `dark` — the Swift took a `ColorScheme` and asked it
 * the same question.
 */
data class Palette(
    val porcelainLight: Argb,
    val porcelainMid: Argb,
    val porcelainDark: Argb,
    val porcelainShadow: Argb,

    val chromeLight: Argb,
    val chromeMid: Argb,
    val chromeDark: Argb,

    val waterLight: Argb,
    val waterDark: Argb,
    val foam: Argb,

    val roomTop: Argb,
    val roomBottom: Argb,
    val tile: Argb,
    val ink: Argb,
    val accent: Argb,
) {
    companion object {

        fun standard(dark: Boolean) = Palette(
            porcelainLight = rgb(1.00, 1.00, 1.00),
            porcelainMid = if (dark) rgb(0.87, 0.89, 0.92) else rgb(0.95, 0.96, 0.98),
            porcelainDark = if (dark) rgb(0.72, 0.75, 0.80) else rgb(0.84, 0.87, 0.91),
            porcelainShadow = rgb(0.38, 0.44, 0.53),
            chromeLight = rgb(0.98, 0.99, 1.00),
            chromeMid = rgb(0.76, 0.80, 0.85),
            chromeDark = rgb(0.42, 0.47, 0.54),
            waterLight = rgb(0.55, 0.82, 0.95),
            waterDark = rgb(0.11, 0.42, 0.68),
            foam = rgb(0.96, 0.99, 1.00),
            roomTop = if (dark) rgb(0.09, 0.12, 0.17) else rgb(0.83, 0.92, 0.95),
            roomBottom = if (dark) rgb(0.05, 0.07, 0.10) else rgb(0.68, 0.83, 0.89),
            tile = Argb.White.opacity(if (dark) 0.05 else 0.35),
            ink = if (dark) rgb(0.90, 0.94, 0.98) else rgb(0.10, 0.18, 0.27),
            accent = rgb(0.11, 0.52, 0.78),
        )

        /** Weathered pine, tin fittings, and water you would rather not look at. */
        fun outhouse(dark: Boolean) = Palette(
            porcelainLight = rgb(0.72, 0.58, 0.41),
            porcelainMid = if (dark) rgb(0.48, 0.37, 0.25) else rgb(0.62, 0.48, 0.33),
            porcelainDark = if (dark) rgb(0.31, 0.23, 0.15) else rgb(0.44, 0.33, 0.21),
            porcelainShadow = rgb(0.18, 0.13, 0.08),
            chromeLight = rgb(0.78, 0.76, 0.71),
            chromeMid = rgb(0.55, 0.53, 0.48),
            chromeDark = rgb(0.31, 0.29, 0.25),
            waterLight = rgb(0.55, 0.52, 0.30),
            waterDark = rgb(0.27, 0.24, 0.11),
            foam = rgb(0.85, 0.83, 0.68),
            roomTop = if (dark) rgb(0.13, 0.11, 0.08) else rgb(0.79, 0.74, 0.60),
            roomBottom = if (dark) rgb(0.07, 0.06, 0.04) else rgb(0.62, 0.56, 0.42),
            tile = Argb.Black.opacity(if (dark) 0.16 else 0.10),
            ink = if (dark) rgb(0.93, 0.89, 0.79) else rgb(0.20, 0.15, 0.08),
            accent = rgb(0.53, 0.38, 0.16),
        )

        /** Cream porcelain, brass, and a great deal of self-regard. */
        fun victorian(dark: Boolean) = Palette(
            porcelainLight = rgb(1.00, 0.99, 0.95),
            porcelainMid = if (dark) rgb(0.88, 0.85, 0.78) else rgb(0.96, 0.94, 0.88),
            porcelainDark = if (dark) rgb(0.71, 0.67, 0.58) else rgb(0.85, 0.81, 0.72),
            porcelainShadow = rgb(0.36, 0.28, 0.18),
            chromeLight = rgb(0.98, 0.91, 0.71),
            chromeMid = rgb(0.80, 0.65, 0.33),
            chromeDark = rgb(0.48, 0.35, 0.12),
            waterLight = rgb(0.63, 0.79, 0.80),
            waterDark = rgb(0.18, 0.38, 0.44),
            foam = rgb(0.97, 0.99, 0.98),
            roomTop = if (dark) rgb(0.14, 0.10, 0.12) else rgb(0.90, 0.84, 0.82),
            roomBottom = if (dark) rgb(0.08, 0.06, 0.07) else rgb(0.76, 0.68, 0.68),
            tile = Argb.White.opacity(if (dark) 0.05 else 0.28),
            ink = if (dark) rgb(0.95, 0.91, 0.85) else rgb(0.24, 0.15, 0.12),
            accent = rgb(0.60, 0.42, 0.14),
        )

        /** Brushed steel, hard light, and no warmth anywhere. */
        fun chrome(dark: Boolean) = Palette(
            porcelainLight = rgb(0.95, 0.96, 0.97),
            porcelainMid = if (dark) rgb(0.63, 0.66, 0.70) else rgb(0.80, 0.83, 0.86),
            porcelainDark = if (dark) rgb(0.42, 0.45, 0.49) else rgb(0.58, 0.62, 0.66),
            porcelainShadow = rgb(0.16, 0.18, 0.21),
            chromeLight = rgb(1.00, 1.00, 1.00),
            chromeMid = rgb(0.66, 0.70, 0.75),
            chromeDark = rgb(0.28, 0.31, 0.35),
            waterLight = rgb(0.72, 0.88, 0.94),
            waterDark = rgb(0.16, 0.36, 0.50),
            foam = rgb(1.00, 1.00, 1.00),
            roomTop = if (dark) rgb(0.10, 0.11, 0.13) else rgb(0.80, 0.84, 0.87),
            roomBottom = if (dark) rgb(0.05, 0.06, 0.07) else rgb(0.63, 0.68, 0.73),
            tile = Argb.White.opacity(if (dark) 0.07 else 0.40),
            ink = if (dark) rgb(0.92, 0.95, 0.98) else rgb(0.12, 0.15, 0.19),
            accent = rgb(0.20, 0.58, 0.78),
        )

        /** Matte white composite under cold instrument light. No sky to speak of. */
        fun orbital(dark: Boolean) = Palette(
            porcelainLight = rgb(0.97, 0.97, 0.99),
            porcelainMid = if (dark) rgb(0.72, 0.73, 0.80) else rgb(0.86, 0.87, 0.92),
            porcelainDark = if (dark) rgb(0.48, 0.49, 0.58) else rgb(0.63, 0.65, 0.73),
            porcelainShadow = rgb(0.13, 0.13, 0.20),
            chromeLight = rgb(0.88, 0.93, 1.00),
            chromeMid = rgb(0.55, 0.62, 0.78),
            chromeDark = rgb(0.25, 0.29, 0.42),
            waterLight = rgb(0.62, 0.95, 0.90),
            waterDark = rgb(0.09, 0.44, 0.46),
            foam = rgb(0.86, 1.00, 0.98),
            roomTop = if (dark) rgb(0.03, 0.03, 0.07) else rgb(0.08, 0.09, 0.16),
            roomBottom = rgb(0.01, 0.01, 0.03),
            tile = Argb.White.opacity(0.05),
            ink = rgb(0.90, 0.94, 1.00),
            accent = rgb(0.36, 0.78, 0.86),
        )

        /** One flush in twenty. Worth making a fuss about. */
        fun golden(dark: Boolean) = Palette(
            porcelainLight = rgb(1.00, 0.97, 0.84),
            porcelainMid = rgb(0.98, 0.86, 0.45),
            porcelainDark = rgb(0.85, 0.65, 0.16),
            porcelainShadow = rgb(0.45, 0.32, 0.04),
            chromeLight = rgb(1.00, 0.98, 0.88),
            chromeMid = rgb(0.96, 0.82, 0.38),
            chromeDark = rgb(0.60, 0.44, 0.06),
            waterLight = rgb(1.00, 0.91, 0.60),
            waterDark = rgb(0.72, 0.49, 0.05),
            foam = rgb(1.00, 0.99, 0.92),
            roomTop = if (dark) rgb(0.20, 0.15, 0.03) else rgb(0.99, 0.93, 0.72),
            roomBottom = if (dark) rgb(0.10, 0.07, 0.01) else rgb(0.96, 0.82, 0.48),
            tile = Argb.White.opacity(if (dark) 0.06 else 0.30),
            ink = if (dark) rgb(1.00, 0.95, 0.80) else rgb(0.30, 0.20, 0.01),
            accent = rgb(0.78, 0.55, 0.06),
        )
    }
}
