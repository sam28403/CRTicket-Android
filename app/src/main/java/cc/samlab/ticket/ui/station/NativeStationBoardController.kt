package cc.samlab.ticket.ui.station

import android.net.Uri
import android.webkit.WebView
import androidx.activity.ComponentActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class NativeStationBoardController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    private val boards = linkedMapOf<String, StationBoardState>()
    private var suggestionTarget: StationBoardState? = null
    private var suggestionQuery = ""
    private val generation = AtomicInteger()
    @Volatile private var closed = false

    internal fun stateFor(location: String): StationBoardState =
        board(Uri.parse(location).getQueryParameter("station").orEmpty())

    private fun board(station: String): StationBoardState = boards.getOrPut(station) {
        StationBoardState(station, ::dispatch)
    }

    private fun dispatch(target: StationBoardState, action: String, station: String) {
        suggestionTarget = if (action == "suggest") target else null
        suggestionQuery = station
        val detail = JSONObject().put("action", action).put("station", station)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-station-request',{detail:$detail}))",
            null,
        )
    }

    fun receive(payload: String) {
        if (closed || payload.length > 16 * 1024 * 1024) return
        val value = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (value.optString("kind") == "suggestions") {
            val query = value.optString("query")
            val items = value.optJSONArray("suggestions") ?: return
            val names = List(items.length()) { items.getJSONObject(it).getString("value") }
            activity.runOnUiThread {
                val target = suggestionTarget
                if (!closed && query == suggestionQuery && target?.station == query) {
                    target.suggestions = names
                }
            }
            return
        }
        val token = generation.incrementAndGet()
        worker.execute {
            val data = value.optJSONObject("tabs")
            val parsed = StationTab.entries.filter { it != StationTab.MAP }.associateWith { tab ->
                val rows = data?.optJSONArray(tab.key)
                if (rows == null) emptyList()
                else List(rows.length()) { parseStationRow(rows.getJSONObject(it)) }
            }
            activity.runOnUiThread {
                if (!closed && token == generation.get()) {
                    val state = board(value.optString("station"))
                    state.station = value.optString("station")
                    state.loading = value.optBoolean("loading")
                    state.error = value.optString("error")
                    state.date = if (data == null) "" else value.optString("date")
                    state.resultStation = if (data == null) "" else value.optString("resultStation")
                    if (data != null) state.preparePresentation(state.date)
                    state.tabs = parsed
                    state.revision++
                }
            }
        }
    }

    fun close() {
        closed = true
        boards.clear()
        suggestionTarget = null
    }
}
