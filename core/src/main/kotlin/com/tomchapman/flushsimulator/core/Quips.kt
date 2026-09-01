package com.tomchapman.flushsimulator.core

import kotlin.random.Random

/**
 * The things the app says to you after you have flushed a toilet on your phone.
 *
 * A class rather than an object because it remembers the last line it gave out, and
 * because the engine's tests want a seeded [Random] rather than a real one. The Swift
 * pinned the same state to the main actor; here it simply belongs to an instance.
 */
class Quips(private val random: Random = Random.Default) {

    /** Never the same line twice in a row, which is most of what makes it feel written. */
    private var lastLine: String? = null

    fun afterFlushLine(): String = pick(afterFlush)
    fun busyLine(): String = pick(whileBusy)
    fun goldenLine(): String = pick(golden)

    private fun pick(lines: List<String>): String {
        val choices = if (lines.size > 1) lines.filter { it != lastLine } else lines
        val line = choices.randomOrNull(random) ?: lines[0]
        lastLine = line
        return line
    }

    companion object {

        /** A line for round numbers, because round numbers deserve acknowledgement. */
        fun milestone(count: Int): String? = when (count) {
            1 -> "Your first flush. They grow up so fast."
            10 -> "Ten flushes. A hobby is forming."
            25 -> "25 flushes. This is a lifestyle now."
            50 -> "50 flushes. Someone should check on you."
            100 -> "100 flushes. Impressive. Slightly worrying."
            250 -> "250 flushes. You ARE the plumbing."
            500 -> "500 flushes. Historians will study this."
            1000 -> "1,000 flushes. There is nothing left to teach you."
            else -> null
        }

        private val afterFlush = listOf(
            "Whoosh. Textbook.",
            "Gone. Reduced to atoms.",
            "Somewhere, a plumber nodded.",
            "Judges: 10, 10, 9.5.",
            "That one had real range.",
            "The porcelain remembers.",
            "Water bill: +$0.004",
            "Certified fresh.",
            "Nothing but net.",
            "Balance has been restored to the bowl.",
            "Smooth. Professional. Devastating.",
            "The tank respects you now.",
            "A masterclass in handle work.",
            "Local plumbing: shaken.",
            "10/10, would flush again.",
            "That's going in the highlight reel.",
            "Physics: satisfied.",
            "No notes.",
            "Somewhere, a duck applauded.",
            "The swirl was, frankly, art.",
            "Clean exit. No witnesses.",
            "You've still got it.",
        )

        private val whileBusy = listOf(
            "Let it finish, champ.",
            "Patience. The tank is refilling.",
            "One at a time. House rules.",
            "It's still going. Look at it go.",
            "Easy, tiger.",
            "You cannot rush a classic.",
        )

        private val golden = listOf(
            "A GOLDEN FLUSH. Tell someone.",
            "GOLDEN FLUSH. The rarest swirl.",
            "GOLDEN FLUSH. You lucky thing.",
        )
    }
}
