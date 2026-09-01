package com.tomchapman.flushsimulator.core

/** Wholly unearned titles, handed out for pushing a lever. */
data class Rank(
    val threshold: Int,
    val title: String,
    /**
     * The icon this rank wears. Carried as the original SF Symbol name, which is a
     * stable identifier rather than an Apple dependency — the Compose layer owns the
     * mapping to a Material symbol or a hand-drawn vector.
     */
    val symbol: String,
) {
    companion object {
        val all: List<Rank> = listOf(
            Rank(0, "Bathroom Rookie", "figure.walk"),
            Rank(1, "Handle Enthusiast", "hand.point.up.left.fill"),
            Rank(5, "Certified Flusher", "checkmark.seal.fill"),
            Rank(15, "Porcelain Apprentice", "drop.fill"),
            Rank(40, "Chain Puller, 1st Class", "link"),
            Rank(80, "Master of Ceremonies", "sparkles"),
            Rank(150, "Duke of the Water Closet", "shield.lefthalf.filled"),
            Rank(300, "Sultan of Swirl", "tornado"),
            Rank(600, "Grand Poobah of Plumbing", "wrench.and.screwdriver.fill"),
            Rank(1000, "Their Royal Flushness", "crown.fill"),
        )

        fun current(flushes: Int): Rank = all.lastOrNull { flushes >= it.threshold } ?: all[0]

        fun next(after: Int): Rank? = all.firstOrNull { after < it.threshold }

        /** Progress from the current rank to the next one, 0..1. */
        fun progress(flushes: Int): Double {
            val now = current(flushes)
            val next = next(flushes) ?: return 1.0
            val span = (next.threshold - now.threshold).toDouble()
            if (span <= 0) return 1.0
            return ((flushes - now.threshold) / span).coerceIn(0.0, 1.0)
        }
    }
}
