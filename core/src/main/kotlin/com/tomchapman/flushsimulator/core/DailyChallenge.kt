package com.tomchapman.flushsimulator.core

import java.text.NumberFormat
import java.time.ZoneId

/**
 * One puzzle a day, the same for everybody, one attempt.
 *
 * Endless play has no reason to bring you back tomorrow — the tally only ever goes
 * up. A daily is the cheapest fix for that: a fixed set of conditions derived from the
 * date itself, so nothing has to be fetched or agreed on, and a score you can compare
 * because everyone got the same bowl.
 *
 * "Everyone" includes the iPhone next to you, which is why [stamp] counts days from
 * Apple's 2001 reference date rather than the Unix epoch the rest of this module uses:
 * the generator is seeded with it, and two phones only agree if they seed alike.
 */
data class DailyChallenge(
    /** Days since 2001-01-01. The seed, and the number on the badge. */
    val stamp: Int,
    val fixtureId: String,
    /** How dirty it starts. Neglect you inherit rather than caused. */
    val startingGrime: Double,
    /** Squares the day asks for. Hitting it exactly pays a bonus. */
    val paperTarget: Int,
) {
    val fixture: Fixture get() = Fixture.withId(fixtureId)

    /** Which day this is, counting from the first one. */
    val number: Int get() = stamp - 9_300

    companion object {
        /** Flushes in a run. Short on purpose: a daily should be one sitting. */
        const val FLUSH_COUNT = 5

        /** What hitting the paper target is worth. */
        const val TARGET_BONUS = 1.5

        /** 2001-01-01 as a Unix epoch day. */
        const val APPLE_EPOCH_DAY = 11_323

        fun today(millis: Long, zone: ZoneId = ZoneId.systemDefault()): DailyChallenge =
            forStamp(Standings.stamp(millis, zone) - APPLE_EPOCH_DAY)

        /** Derived from the date and nothing else, so two phones agree without talking. */
        fun forStamp(stamp: Int): DailyChallenge {
            val rng = SplitMix(stamp.toLong())
            // Drawn in this order, because the iOS app draws them in this order.
            val pick = Fixture.all[rng.nextBelow(Fixture.all.size.toLong()).toInt()]
            val grime = rng.nextBelow(50).toDouble() / 100          // 0 ... 0.49
            val target = 1 + rng.nextBelow(5).toInt()                // 1 ... 5
            return DailyChallenge(stamp, pick.id, grime, target)
        }
    }
}

/** How one flush of a daily went. */
enum class DailyMark(val emoji: String) {
    Golden("🟨"),   // yellow square
    Perfect("🟩"),  // green
    Good("🟦"),     // blue
    Poor("⬜"),           // white
    Clogged("🟥"),  // red
}

/** Your attempt at a given day. */
data class DailyResult(
    val stamp: Int,
    val marks: List<DailyMark> = emptyList(),
    val score: Int = 0,
) {
    val isComplete: Boolean get() = marks.size >= DailyChallenge.FLUSH_COUNT

    /** What gets copied out. No link, no tracking, just the grid. */
    fun shareText(challenge: DailyChallenge): String =
        "Flush Simulator — Daily #${challenge.number}\n" +
            marks.joinToString("") { it.emoji } + "\n" +
            "${NumberFormat.getIntegerInstance().format(score)} points · ${challenge.fixture.name}"

    fun save(settings: Settings) {
        settings.putString(KEY, "$stamp,$score," + marks.joinToString(";") { it.name })
    }

    companion object {
        private const val KEY = "dailyResult"

        /** Today's attempt, or null. Yesterday's attempt is not today's. */
        fun load(settings: Settings, todayStamp: Int): DailyResult? {
            val text = settings.getString(KEY) ?: return null
            val parts = text.split(",", limit = 3)
            if (parts.size != 3) return null
            val stamp = parts[0].toIntOrNull() ?: return null
            val score = parts[1].toIntOrNull() ?: return null
            val marks = parts[2].split(";").filter { it.isNotEmpty() }.map { name ->
                DailyMark.entries.firstOrNull { it.name == name } ?: return null
            }
            return if (stamp == todayStamp) DailyResult(stamp, marks, score) else null
        }

        fun clear(settings: Settings) = settings.remove(KEY)
    }
}

/**
 * A small deterministic generator, so a given day is the same day everywhere.
 *
 * Bit for bit the iOS app's, which does this on `UInt64`. Kotlin's `Long` is signed,
 * but wrapping multiplication and addition are the same bits either way; only the
 * shifts have to be told to be logical, and the modulo has to be told to be unsigned.
 */
class SplitMix(seed: Long) {
    private var state: Long = seed * GOLDEN + INIT

    fun next(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * M1
        z = (z xor (z ushr 27)) * M2
        return z xor (z ushr 31)
    }

    fun nextBelow(bound: Long): Long =
        if (bound == 0L) 0L else java.lang.Long.remainderUnsigned(next(), bound)

    private companion object {
        // Written as unsigned so the constants read the way the iOS source has them.
        val GOLDEN = 0x9E37_79B9_7F4A_7C15uL.toLong()
        val INIT = 0x1234_5678_9ABC_DEF1uL.toLong()
        val M1 = 0xBF58_476D_1CE4_E5B9uL.toLong()
        val M2 = 0x94D0_49BB_1331_11EBuL.toLong()
    }
}
