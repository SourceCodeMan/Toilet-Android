package com.tomchapman.flushsimulator.board

import com.tomchapman.flushsimulator.core.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Reading what the board says back.
 *
 * Robolectric only for `org.json`, which is a platform class with no JVM equivalent.
 * Nothing here goes near the network — the parsing and the identity are the parts
 * worth pinning down, and both are pure.
 */
@RunWith(RobolectricTestRunner::class)
class BoardClientTest {

    @Test
    fun `a board reply becomes rows`() {
        val rows = BoardClient.parseBoard(
            """{"entries":[
                 {"rank":1,"name":"Tom","lifetime":900,"bestDay":4200,"isYou":true},
                 {"rank":2,"name":"Someone","lifetime":410,"bestDay":900,"isYou":false}
               ]}""",
        )
        assertEquals(2, rows.size)
        assertEquals(BoardEntry(1, "Tom", 900, 4_200, isYou = true), rows[0])
        assertEquals("Someone", rows[1].name)
        assertTrue(!rows[1].isYou)
    }

    @Test
    fun `an empty board is empty, not an error`() {
        assertEquals(emptyList<BoardEntry>(), BoardClient.parseBoard("""{"entries":[]}"""))
        assertEquals(emptyList<BoardEntry>(), BoardClient.parseBoard("{}"))
    }

    @Test
    fun `one malformed row costs that row, not the board`() {
        val rows = BoardClient.parseBoard(
            """{"entries":[
                 {"rank":1,"name":"Tom","lifetime":900,"bestDay":10,"isYou":false},
                 {"rank":2,"lifetime":410},
                 {"rank":3,"name":"","lifetime":5},
                 {"rank":4,"name":"Last","lifetime":1,"bestDay":0,"isYou":false}
               ]}""",
        )
        assertEquals(listOf("Tom", "Last"), rows.map { it.name })
    }

    @Test
    fun `missing numbers read as zero rather than throwing`() {
        val rows = BoardClient.parseBoard("""{"entries":[{"name":"Sparse"}]}""")
        assertEquals(1, rows.size)
        assertEquals(0, rows[0].lifetime)
        // Rank falls back to the row's own position, so the list still reads 1, 2, 3.
        assertEquals(1, rows[0].rank)
    }

    @Test
    fun `the board's own complaint is preferred to the status code`() {
        assertEquals("name must be 1 to 24 printable characters",
            BoardClient.problem(400, """{"error":"name must be 1 to 24 printable characters"}"""))
        assertEquals("The board said 500.", BoardClient.problem(500, "not json"))
        assertEquals("The board said 502.", BoardClient.problem(502, ""))
    }

    @Test
    fun `a player id is made once and then kept`() {
        val settings = MemorySettings()
        val first = BoardClient(settings).playerId
        assertTrue("looks like a uuid", first.matches(Regex("[0-9a-f-]{36}")))
        assertEquals("a second look gives the same player", first, BoardClient(settings).playerId)
        assertNotEquals("a different device is a different player", first, BoardClient(MemorySettings()).playerId)
    }

    @Test
    fun `with no url compiled in the board says so instead of hanging`() = runBlocking {
        val client = BoardClient(MemorySettings(), baseUrl = "")
        assertEquals(BoardState.NotConfigured, client.refresh(lifetime = 10, bestDay = 20))
    }

    @Test
    fun `with no name the board asks for one before reaching out`() = runBlocking {
        val client = BoardClient(MemorySettings(), baseUrl = "https://example.invalid")
        assertEquals(BoardState.NeedsName, client.refresh(lifetime = 10, bestDay = 20))

        client.playerName = "Tom"
        // Now it will actually try, and fail, because that host does not exist.
        assertTrue(client.refresh(10, 20) is BoardState.Failed)
    }
}

private class MemorySettings : Settings {
    private val values = LinkedHashMap<String, Any>()
    override fun getInt(key: String, default: Int) = (values[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getDouble(key: String, default: Double) = (values[key] as? Double) ?: default
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
