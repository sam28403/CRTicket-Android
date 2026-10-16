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
            val unavailable = activity.getString(R.string.display_unavailable)
            val parsed = data?.let { List(it.length()) { index ->
                val row = data.getJSONObject(index)
                val duration = row.optString("duration")
                val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
                val minutes = if (parts.size == 2) parts[0] * 60 + parts[1] else 0
                val km = row.optDouble("mileage", Double.NaN)
                val distance = km.takeIf { it.isFinite() && it >= 0 }
                val speed = distance?.takeIf { minutes > 0 }?.let { it * 60 / minutes }
                TrainListRow(
                    row.optString("train"), row.optString("from"), row.optString("to"),
                    duration.takeIf { it.isNotBlank() && it != "null" } ?: unavailable,
                    if (row.isNull("stationCount")) unavailable else row.optString("stationCount"),
                    if (distance == null) unavailable else row.optString("mileage"),
                    if (speed == null) unavailable
                    else activity.getString(R.string.display_decimal_one, speed),
                    sourceIndex = index,
                    distanceKm = distance,
                    speedKmh = speed,
                    stationCount = row.optDouble("stationCount", Double.NaN)
                        .takeIf { it.isFinite() && it >= 0 },
                )
            } }
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
