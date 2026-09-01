package com.tomchapman.flushsimulator.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Your best days, and how they survive being written to disk. */
class StandingsTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    /** 2026-09-01T12:00Z. */
    private val noon = 1_788_264_000_000L
    private val day = 86_400_000L

    @Test
    fun `the stamp is one number per local day`() {
        assertEquals(0, Standings.stamp(0L, utc))                    // 1970-01-01
        assertEquals(1, Standings.stamp(day, utc))
        // Same day either side of noon, and the boundary lands where the zone says.
        assertEquals(Standings.stamp(noon, utc), Standings.stamp(noon + 3_600_000, utc))
        assertEquals(Standings.stamp(noon, utc) + 1, Standings.stamp(noon + day, utc))
    }

    @Test
    fun `flushes on the same day land on the same row`() {
        var s = Standings()
        repeat(3) { s = s.record(golden = false, streak = 0, points = 100, atMillis = noon, zone = utc) }
        assertEquals(1, s.days.size)
        assertEquals(3, s.days[0].flushes)
        assertEquals(300, s.days[0].score)
    }

    @Test
    fun `a new day starts a new row, newest first`() {
        var s = Standings()
        s = s.record(false, 0, 100, noon, utc)
        s = s.record(false, 0, 100, noon + day, utc)
        assertEquals(2, s.days.size)
        assertTrue(s.days[0].stamp > s.days[1].stamp, "days should be newest first")
    }

    @Test
    fun `a day keeps its best streak and counts its gold`() {
        var s = Standings()
        s = s.record(golden = false, streak = 2, points = 100, atMillis = noon, zone = utc)
        s = s.record(golden = true, streak = 5, points = 300, atMillis = noon, zone = utc)
        s = s.record(golden = false, streak = 1, points = 100, atMillis = noon, zone = utc)
        val today = s.today(noon, utc)!!
        assertEquals(3, today.flushes)
        assertEquals(1, today.golden)
        assertEquals(5, today.bestStreak, "the best of the day, not the last of it")
        assertEquals(500, today.score)
    }

    @Test
    fun `the board ranks on score, ties broken by the more recent day`() {
        var s = Standings()
        s = s.record(false, 0, 100, noon, utc)              // older, 100
        s = s.record(false, 0, 500, noon + day, utc)        // middle, 500
        s = s.record(false, 0, 100, noon + 2 * day, utc)    // newer, 100
        val board = s.board
        assertEquals(500, board[0].score)
        assertEquals(Standings.stamp(noon + 2 * day, utc), board[1].stamp, "tie goes to the newer day")
    }

    @Test
    fun `the board shows no more rows than it promises`() {
        var s = Standings()
        repeat(30) { s = s.record(false, 0, it + 1, noon + it * day, utc) }
        assertEquals(Standings.BOARD_LENGTH, s.board.size)
    }

    @Test
    fun `history is trimmed but today is never trimmed away`() {
        var s = Standings()
        repeat(Standings.HISTORY_LIMIT + 25) { s = s.record(false, 0, 100, noon + it * day, utc) }
        assertEquals(Standings.HISTORY_LIMIT, s.days.size)
        val newest = noon + (Standings.HISTORY_LIMIT + 24) * day
        assertEquals(Standings.stamp(newest, utc), s.days.first().stamp, "the oldest days go, not the newest")
    }

    @Test
    fun `todays rank is null until today makes the board`() {
        var s = Standings()
        // Ten better days behind us.
        repeat(Standings.BOARD_LENGTH) { s = s.record(false, 0, 1_000, noon - (it + 1) * day, utc) }
        assertNull(s.todaysRank(noon, utc), "no flushes today at all")

        s = s.record(false, 0, 1, noon, utc)
        assertNull(s.todaysRank(noon, utc), "one point should not make a full board")

        s = s.record(false, 0, 99_999, noon, utc)
        assertEquals(1, s.todaysRank(noon, utc))
    }

    @Test
    fun `a save round-trips through settings`() {
        val settings = MapSettings()
        var s = Standings()
        s = s.record(golden = true, streak = 4, points = 420, atMillis = noon, zone = utc)
        s = s.record(golden = false, streak = 0, points = 140, atMillis = noon - day, zone = utc)
        s.save(settings)

        val loaded = Standings.load(settings)
        assertEquals(s, loaded)
    }

    @Test
    fun `a corrupted save starts fresh rather than throwing`() {
        val junk = listOf(
            "", "nonsense", "1,2,3", "1,2,3,4,5,6", "a,b,c,d,e", ";;;",
            "1,2,3,4,", ",,,,", "999999999999999999999,1,1,1,1",
        )
        for (bad in junk) {
            val settings = MapSettings(mapOf("standings" to bad))
            assertEquals(Standings(), Standings.load(settings), "junk: $bad")
        }
    }

    @Test
    fun `a malformed row is dropped and the good ones survive`() {
        val settings = MapSettings(mapOf("standings" to "1,1,0,0,10;broken;3,9,0,0,90"))
        val loaded = Standings.load(settings)
        assertEquals(2, loaded.days.size)
        assertEquals(listOf(10, 90), loaded.days.map { it.score })
    }

    @Test
    fun `clear removes the save`() {
        val settings = MapSettings()
        Standings().record(false, 0, 100, noon, utc).save(settings)
        assertTrue(Standings.load(settings).days.isNotEmpty())
        Standings.clear(settings)
        assertEquals(Standings(), Standings.load(settings))
    }

    @Test
    fun `labels read as today, yesterday, then a date`() {
        val today = Standings.stamp(noon, utc)
        assertEquals("Today", Standings.label(today, noon, utc))
        assertEquals("Yesterday", Standings.label(today - 1, noon, utc))
        val older = Standings.label(today - 5, noon, utc)
        assertTrue(older != "Today" && older != "Yesterday", "got $older")
        assertTrue(older.any { it.isDigit() }, "a date should carry a day number: $older")
    }
}

