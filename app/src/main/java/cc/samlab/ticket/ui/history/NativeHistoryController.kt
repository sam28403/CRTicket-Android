package cc.samlab.ticket.ui.history

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class NativeHistoryController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    internal val ui = HistoryEditorState()
    internal var stations by mutableStateOf<List<HistoryStation>>(emptyList())
        private set
    internal var seats by mutableStateOf<List<HistoryChoice>>(emptyList())
        private set
    internal var themes by mutableStateOf<List<HistoryChoice>>(emptyList())
        private set
    internal var airDisabledSeats by mutableStateOf<Set<String>>(emptySet())
        private set
    private var defaults = JSONObject()
    private var sourceRows = emptyMap<String, JSONObject>()
    private var pendingOperation: String? = null
    internal var rows by mutableStateOf<List<HistoryRecord>>(emptyList())
        private set
    internal var sort by mutableStateOf(HistorySort.NEWEST)
        private set
    internal var loading by mutableStateOf(true)
        private set
    internal var error by mutableStateOf("")
        private set
    internal var distance by mutableStateOf(0.0)
        private set
    internal var money by mutableStateOf(0.0)
        private set
    private val generation = AtomicInteger()
    @Volatile private var closed = false

    fun receive(payload: String) {
        if (payload.length > 16 * 1024 * 1024) return
        val token = generation.incrementAndGet()
        worker.execute {
            val snapshot = runCatching { JSONObject(payload) }.getOrNull() ?: return@execute
            val data = snapshot.optJSONArray("rows") ?: return@execute
            val catalogue = snapshot.optJSONArray("stations")?.let { values ->
                List(values.length()) { index ->
                    val station = values.getJSONArray(index)
                    HistoryStation(station.optString(0), station.optString(1), station.optString(2))
                }
            }
            val records = List(data.length()) { index ->
                val row = data.getJSONObject(index)
                HistoryRecord(
                    row.optString("id"), row.optString("date"), row.optString("time"),
                    row.optString("trainNo"), row.optString("from"), row.optString("to"),
                    row.optString("seatType"), row.optString("seatNo"), row.optString("number"),
                    row.optDouble("price", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    row.optInt("useCredit") == 1,
                )
            }
            activity.runOnUiThread {
                if (!closed && catalogue != null) stations = catalogue
                if (!closed && token == generation.get()) {
                    rows = records
                    loading = snapshot.optBoolean("loading")
                    error = snapshot.optString("error")
                    distance = snapshot.optDouble("distance", 0.0).takeIf { it.isFinite() } ?: 0.0
                    money = snapshot.optDouble("money", 0.0).takeIf { it.isFinite() } ?: 0.0
                    sourceRows = List(data.length()) { data.getJSONObject(it) }
                        .associateBy { it.optString("id") }
                    snapshot.optJSONObject("defaults")?.let { defaults = it }
                    fun choices(key: String): List<HistoryChoice> {
                        val options = snapshot.optJSONArray(key) ?: return emptyList()
                        return List(options.length()) { index ->
                            val item = options.getJSONObject(index)
                            HistoryChoice(item.optString("value"), item.optString("label"), item.optBoolean("disabled"))
                        }
                    }
                    if (snapshot.has("seats")) seats = choices("seats")
                    if (snapshot.has("themes")) themes = choices("themes")
                    snapshot.optJSONArray("airDisabledSeats")?.let { values ->
                        airDisabledSeats = List(values.length()) { values.getString(it) }.toSet()
                    }
                }
            }
        }
    }

    internal fun changeSort(value: HistorySort) { sort = value }

    internal fun action(action: String, id: String = "") {
        if (ui.busy) return
        when (action) {
            "create" -> ui.open(null, defaults)
            "edit" -> sourceRows[id]?.let { ui.open(id, it) }
            "delete" -> {
                ui.error = ""
                ui.deleting = rows.firstOrNull { it.id == id }
            }
            "refresh" -> if (!loading) dispatch(JSONObject().put("action", action))
            else -> dispatch(JSONObject().put("action", action).put("id", id))
        }
    }

    internal fun submit() {
        val draft = ui.draft ?: return
        mutate(JSONObject().put("action", "save").put("id", draft.id).put("ticket", draft.json()))
    }

    internal fun confirmDelete() {
        val row = ui.deleting ?: return
        mutate(JSONObject().put("action", "remove").put("id", row.id))
    }

    private fun mutate(detail: JSONObject) {
        if (ui.busy) return
        val operation = java.util.UUID.randomUUID().toString()
        pendingOperation = operation
        ui.busy = true
        ui.error = ""
        dispatch(detail.put("operation", operation))
        android.os.Handler(activity.mainLooper).postDelayed({
            if (!closed && pendingOperation == operation) {
                pendingOperation = null
                ui.busy = false
                ui.error = "操作超时，请刷新记录后重试"
            }
        }, 60_000)
    }

    fun receiveOperation(payload: String) {
        val value = runCatching { JSONObject(payload) }.getOrNull() ?: return
        activity.runOnUiThread {
            if (closed || value.optString("operation") != pendingOperation) return@runOnUiThread
            pendingOperation = null
            ui.busy = false
            if (value.optBoolean("success")) {
                ui.deleting = null
                ui.closeDetail()
            } else ui.error = value.optString("message", "操作失败，请重试")
        }
    }

    private fun dispatch(detail: JSONObject) {
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-history-action',{detail:$detail}))",
            null,
        )
    }

    fun close() { closed = true }

    fun save(state: android.os.Bundle) = ui.save(state)

    fun restore(state: android.os.Bundle?) = ui.restore(state)
}
