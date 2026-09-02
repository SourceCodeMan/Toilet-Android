package com.tomchapman.flushsimulator.board

import com.tomchapman.flushsimulator.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Where the global board lives.
 *
 * Empty until `board/` is deployed — see board/README.md. Leaving it empty is not a
 * failure state: the app says the board is not live yet and carries on keeping your
 * own days, which is exactly what the iOS app does with its unconfigured Game Center.
 */
const val BOARD_URL = ""

/** One row on the global board. */
data class BoardEntry(
    val rank: Int,
    val name: String,
    val lifetime: Int,
    val bestDay: Int,
    val isYou: Boolean,
)

/** Everything the Global tab can be showing. */
sealed interface BoardState {
    data object Loading : BoardState

    /** No URL compiled in, so there is nothing to ask. */
    data object NotConfigured : BoardState

    /** The board needs something to call you before you can appear on it. */
    data object NeedsName : BoardState

    data class Failed(val why: String) : BoardState
    data class Ready(val entries: List<BoardEntry>) : BoardState
}

/**
 * The global board, over plain HTTP.
 *
 * No HTTP library: two requests against a JSON endpoint is what `HttpURLConnection`
 * is for, and `org.json` is already in the platform. Adding OkHttp to make two calls
 * would be more code to own, not less.
 */
class BoardClient(
    private val settings: Settings,
    private val baseUrl: String = BOARD_URL,
) {

    /** A player is a uuid this device made up once and kept. */
    val playerId: String
        get() = settings.getString(KEY_ID) ?: UUID.randomUUID().toString().also {
            settings.putString(KEY_ID, it)
        }

    var playerName: String?
        get() = settings.getString(KEY_NAME)
        set(value) {
            if (value == null) settings.remove(KEY_NAME) else settings.putString(KEY_NAME, value)
        }

    /**
     * Push your totals up, then read the board back.
     *
     * Submitting first means the board you are shown already includes the flush that
     * sent you looking at it.
     */
    suspend fun refresh(lifetime: Int, bestDay: Int): BoardState {
        if (baseUrl.isBlank()) return BoardState.NotConfigured
        val name = playerName ?: return BoardState.NeedsName

        return withContext(Dispatchers.IO) {
            try {
                submit(name, lifetime, bestDay)
                BoardState.Ready(board())
            } catch (e: IOException) {
                // A board that cannot be reached is worth saying out loud; it is not
                // worth taking the app down for.
                BoardState.Failed(e.message ?: "The board didn't answer.")
            }
        }
    }

    private fun submit(name: String, lifetime: Int, bestDay: Int) {
        val body = JSONObject()
            .put("id", playerId)
            .put("name", name)
            .put("lifetime", lifetime)
            .put("bestDay", bestDay)
            .toString()

        val connection = open("/v1/scores", "POST")
        connection.doOutput = true
        connection.setRequestProperty("content-type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        connection.readOrThrow()
    }

    private fun board(): List<BoardEntry> {
        val connection = open("/v1/board?id=$playerId", "GET")
        return parseBoard(connection.readOrThrow())
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
        }

    private fun HttpURLConnection.readOrThrow(): String = try {
        if (responseCode !in 200..299) {
            val detail = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IOException(problem(responseCode, detail))
        }
        inputStream.bufferedReader().use { it.readText() }
    } finally {
        disconnect()
    }

    companion object {
        private const val KEY_ID = "playerId"
        private const val KEY_NAME = "playerName"

        /** The board's own message if it sent one, otherwise the status code. */
        internal fun problem(code: Int, body: String): String {
            val said = runCatching { JSONObject(body).optString("error") }.getOrNull()
            return if (!said.isNullOrBlank()) said else "The board said $code."
        }

        /**
         * Reads the board's reply.
         *
         * A row missing a field is dropped rather than throwing: one malformed entry
         * should cost you that row, not the whole board.
         */
        internal fun parseBoard(json: String): List<BoardEntry> {
            val rows = JSONObject(json).optJSONArray("entries") ?: return emptyList()
            return (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val name = row.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BoardEntry(
                    rank = row.optInt("rank", i + 1),
                    name = name,
                    lifetime = row.optInt("lifetime"),
                    bestDay = row.optInt("bestDay"),
                    isYou = row.optBoolean("isYou"),
                )
            }
        }
    }
}
