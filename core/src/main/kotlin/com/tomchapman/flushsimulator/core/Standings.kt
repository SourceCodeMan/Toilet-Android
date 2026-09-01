package com.tomchapman.flushsimulator.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What you have flushed, and when.
 *
 * A leaderboard in a one-player app has to rank you against something, so it ranks
 * you against your own days. Each day gets a tally; the board is your best days,
 * with today marked so you can see what you have to beat.
 *
 * Kept as a small rolling history rather than every day forever — the board only ever
 * shows a handful, and nobody needs a diary of this.
 */
data class Standings(
    /** Newest first. */
    val days: List<Day> = emptyList(),
) {

    data class Day(
        /**
         * Days since 1970-01-01, in the local calendar. Cheap to compare, cheap to
         * store, and it does not drift the way a formatted date string would.
         */
        val stamp: Int,
        val flushes: Int,
        val golden: Int,
        val bestStreak: Int,
        /** What the day's flushes were worth, paper and gold included. */
        val score: Int,
    )

    // Recording

    /** Add one flush to today's tally. */
    fun record(
        golden: Boolean,
        streak: Int,
        points: Int,
        atMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Standings {
        val today = stamp(atMillis, zone)
        val index = days.indexOfFirst { it.stamp == today }

        val updated = if (index >= 0) {
            days.toMutableList().also { list ->
                val day = list[index]
                list[index] = day.copy(
                    flushes = day.flushes + 1,
                    golden = day.golden + if (golden) 1 else 0,
                    bestStreak = maxOf(day.bestStreak, streak),
                    score = day.score + points,
                )
            }
        } else {
            (days + Day(today, 1, if (golden) 1 else 0, streak, points))
                .sortedByDescending { it.stamp }
                .take(HISTORY_LIMIT)
        }

        return copy(days = updated)
    }

    // Reading

    fun today(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Day? {
        val today = stamp(atMillis, zone)
        return days.firstOrNull { it.stamp == today }
    }

    /**
     * Best days first, ties broken by the more recent day.
     *
     * Ranked on score rather than raw flushes, so the paper you risk is worth risking.
     * Ranking on flushes made one careful square the only sane play.
     */
    val board: List<Day>
        get() = days
            .sortedWith(compareByDescending<Day> { it.score }.thenByDescending { it.stamp })
            .take(BOARD_LENGTH)

    /** Where today sits on that board, 1-based, or null if it has not made it. */
    fun todaysRank(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val today = today(atMillis, zone) ?: return null
        val index = board.indexOfFirst { it.stamp == today.stamp }
        return if (index >= 0) index + 1 else null
    }

    // Storage

    fun save(settings: Settings) {
        settings.putString(
            KEY,
            days.joinToString(";") { "${it.stamp},${it.flushes},${it.golden},${it.bestStreak},${it.score}" },
        )
    }

    companion object {

        /** How many days of history to keep. */
        const val HISTORY_LIMIT = 60

        /** How many rows the board shows. */
        const val BOARD_LENGTH = 10

        private const val KEY = "standings"

        fun stamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay().toInt()

        fun load(settings: Settings): Standings {
            val text = settings.getString(KEY) ?: return Standings()
            val days = text.split(";").mapNotNull { row ->
                // Five numbers, or the row is not one of ours and is dropped. A save
                // that has been corrupted or hand-edited comes back short rather than
                // throwing, and the player starts fresh.
                val n = row.split(",")
                if (n.size != 5) return@mapNotNull null
                val stamp = n[0].toIntOrNull() ?: return@mapNotNull null
                val flushes = n[1].toIntOrNull() ?: return@mapNotNull null
                val golden = n[2].toIntOrNull() ?: return@mapNotNull null
                val bestStreak = n[3].toIntOrNull() ?: return@mapNotNull null
                val score = n[4].toIntOrNull() ?: return@mapNotNull null
                Day(stamp, flushes, golden, bestStreak, score)
            }
            return Standings(days)
        }

        fun clear(settings: Settings) = settings.remove(KEY)

        /** A short label for a row: "Today", "Yesterday", or a date. */
        fun label(
            stamp: Int,
            nowMillis: Long,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): String = when (stamp(nowMillis, zone) - stamp) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> LocalDate.ofEpochDay(stamp.toLong())
                .format(DateTimeFormatter.ofPattern("d MMM", locale))
        }
    }
}
