package cc.samlab.ticket.ui.station

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class NativeStationBoardController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    var station by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var date by mutableStateOf("")
        private set
    var resultStation by mutableStateOf("")
        private set
    internal var tabs by mutableStateOf<Map<StationTab, List<StationBoardRow>>>(emptyMap())
        private set
    private val presentations = linkedMapOf<String, StationBoardPresentation>()
    private var presentation by mutableStateOf(StationBoardPresentation())
    internal var selectedTab: StationTab
        get() = presentation.selectedTab
        set(value) { presentation.selectedTab = value }
    internal var follow: Boolean
        get() = presentation.follow
        set(value) { presentation.follow = value }
    internal val gridState
        get() = presentation.grids.getValue(selectedTab)
    internal var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var revision by mutableStateOf(0)
        private set
    private var suggestionQuery = ""
    private val generation = AtomicInteger()
    @Volatile private var closed = false

    fun editStation(value: String) {
        station = value.take(30)
        suggestionQuery = station
        suggestions = emptyList()
        dispatch("suggest", station)
    }

    fun selectStation(value: String) {
        station = value
        suggestionQuery = ""
        suggestions = emptyList()
    }

    fun request() {
        if (loading) return
        suggestions = emptyList()
        dispatch("query", station)
    }

    private fun dispatch(action: String, station: String) {
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
                if (!closed && query == suggestionQuery) suggestions = names
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
                    station = value.optString("station")
                    loading = value.optBoolean("loading")
                    error = value.optString("error")
                    date = if (data == null) "" else value.optString("date")
                    resultStation = if (data == null) "" else value.optString("resultStation")
                    if (data != null) {
                        val key = "$date:$resultStation"
                        presentation = presentations.getOrPut(key) { StationBoardPresentation() }
                        if (presentations.size > 12) presentations.remove(presentations.keys.first())
                    }
                    tabs = parsed
                    revision++
                }
            }
        }
    }

    fun close() { closed = true }
}
