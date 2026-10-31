package cc.samlab.ticket.ui.station

import android.net.Uri
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class NativeStationBoardController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    internal val quizCounts = QuizCountsBridge(webView)
    private val boards = linkedMapOf<String, StationBoardState>()
    private var discovery = JSONObject()
    internal val saleTimes = cc.samlab.ticket.ui.sale.SaleTimeRepository(activity.applicationContext)
    internal val saleSchedule = cc.samlab.ticket.ui.sale.SaleScheduleSource(activity.applicationContext)
    internal var saleCatalog by mutableStateOf(JSONObject())
        private set

    internal fun requestSaleCatalog() {
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-visited-stations-request'," +
                "{detail:{saleCatalog:true,requestId:0}}))", null,
        )
    }
    internal var visitCards by mutableStateOf<Map<String, JSONObject>>(emptyMap())
        private set
    internal var visitsLoading by mutableStateOf(false)
        private set
    private var visitsRequestId = 0
    private var suggestionTarget: StationBoardState? = null
    private var suggestionQuery = ""
    private val generation = AtomicInteger()
    @Volatile private var closed = false

    internal fun stateFor(location: String): StationBoardState =
        board(Uri.parse(location).getQueryParameter("station").orEmpty())

    internal fun resetInput(location: String) {
        val state = stateFor(location)
        state.selectStation(Uri.parse(location).getQueryParameter("station").orEmpty())
        suggestionTarget = null
        suggestionQuery = ""
    }

    internal fun requestVisitCards(stations: List<String>) {
        visitsRequestId++
        visitsLoading = true
        val detail = JSONObject().put("stations", JSONArray(stations))
            .put("requestId", visitsRequestId)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-visited-stations-request',{detail:$detail}))",
            null,
        )
    }

    private fun board(station: String): StationBoardState = boards.getOrPut(station) {
        StationBoardState(station, ::dispatch).also { it.discovery = discovery }
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
        if (value.optString("kind") == "quizCounts") {
            activity.runOnUiThread { quizCounts.receive(value) }
            return
        }
        if (value.optString("kind") == "saleCatalog") {
            activity.runOnUiThread { if (!closed) saleCatalog = value }
            return
        }
        if (value.optString("kind") == "visits") {
            activity.runOnUiThread {
                if (!closed && value.optInt("requestId") == visitsRequestId) {
                    val rows = value.optJSONArray("stations") ?: JSONArray()
                    visitCards = (0 until rows.length()).associate { index ->
                        val row = rows.getJSONObject(index)
                        row.getString("station") to row
                    }
                    visitsLoading = false
                }
            }
            return
        }
        if (value.optString("kind") == "discovery") {
            activity.runOnUiThread {
                if (!closed) {
                    discovery = value
                    boards.values.forEach { it.discovery = value }
                }
            }
            return
        }
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
