package cc.samlab.ticket.ui.trainlist

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.R
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import org.json.JSONObject
import org.json.JSONArray
import cc.samlab.ticket.ui.railfan.VehicleHistory

class NativeTrainListController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    var date by mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf("")
        private set
    internal var rows by mutableStateOf<List<TrainListRow>>(emptyList())
        private set
    internal var sort by mutableStateOf(TrainListSort.TRAIN)
        private set
    internal var descending by mutableStateOf(false)
        private set
    private var sourceRows: List<TrainListRow> = emptyList()
    private var resetAfterSort = false
    private var sortGeneration = 0
    var revision by mutableStateOf(0)
        private set
    private val generation = java.util.concurrent.atomic.AtomicInteger()
    internal var vehicleRows by mutableStateOf<Map<String, TrainListRow>>(emptyMap())
        private set
    private var vehicleRequestId = 0
    @Volatile private var closed = false

    fun request(selected: String = date) {
        if (loading && selected == date) return
        val detail = JSONObject().put("date", selected)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-train-list-request',{detail:$detail}))",
            null,
        )
    }

    // Called on the bridge thread. Parse and format the full snapshot off the UI thread.
    fun receive(payload: String) {
        if (payload.length > 16 * 1024 * 1024) return
        if (payload.contains("\"vehicleCards\"")) {
            receiveVehicleRows(payload)
            return
        }
        val token = generation.incrementAndGet()
        worker.execute {
            val value = runCatching { JSONObject(payload) }.getOrNull() ?: return@execute
            val selected = value.optString("date")
            val busy = value.optBoolean("loading")
            val message = when (value.optString("errorKey")) {
                "timeout" -> activity.getString(R.string.train_list_timeout)
                "network" -> activity.getString(R.string.train_list_network_error)
                else -> value.optString("error")
            }
            val data = value.optJSONObject("result")?.optJSONArray("trains")
            val parsed = data?.let { parseTrainListRows(activity, it) }
            activity.runOnUiThread {
                if (!closed && token == generation.get()) {
                    if (date != selected) {
                        rows = emptyList()
                        sourceRows = emptyList()
                        sortGeneration++
                        revision++
                    }
                    date = selected
                    loading = busy
                    error = message
                    if (parsed != null) {
                        sourceRows = parsed
                        publishSortedRows()
                    }
                }
            }
        }
    }

    internal fun requestVehicleRows(history: List<VehicleHistory>, keepExisting: Boolean = false) {
        if (!keepExisting) vehicleRows = emptyMap()
        val detail = JSONObject().put("requestId", ++vehicleRequestId).put(
            "records", JSONArray().apply {
                history.forEach { put(JSONObject().put("train", it.train).put("date", it.day)) }
            },
        )
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-vehicle-cards-request',{detail:$detail}))",
            null,
        )
    }

    private fun receiveVehicleRows(payload: String) {
        worker.execute {
            val value = runCatching { JSONObject(payload) }.getOrNull() ?: return@execute
            val data = value.optJSONArray("rows") ?: return@execute
            val parsed = parseTrainListRows(activity, data)
            val result = parsed.mapIndexed { index, row ->
                data.getJSONObject(index).getString("key") to row
            }.toMap()
            activity.runOnUiThread {
                if (!closed && value.optInt("requestId") == vehicleRequestId) vehicleRows = result
            }
        }
    }

    internal fun changeSort(
        sort: TrainListSort = this.sort,
        descending: Boolean = if (sort != this.sort) sort != TrainListSort.TRAIN else this.descending,
    ) {
        if (this.sort == sort && this.descending == descending) return
        this.sort = sort
        this.descending = descending
        resetAfterSort = true
        publishSortedRows()
    }

    private fun publishSortedRows() {
        val token = ++sortGeneration
        val source = sourceRows
        val selectedSort = sort
        val selectedDescending = descending
        worker.execute {
            val sorted = sortTrainList(source, selectedSort, selectedDescending)
            activity.runOnUiThread {
                if (!closed && token == sortGeneration) {
                    rows = sorted
                    if (resetAfterSort) {
                        revision++
                        resetAfterSort = false
                    }
                }
            }
        }
    }

    internal fun open(row: TrainListRow, navigate: (String) -> Unit) {
        navigate("/train?train=${android.net.Uri.encode(row.train)}&date=$date&source=llt")
    }

    fun close() { closed = true }
}
