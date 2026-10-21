package cc.samlab.ticket.ui.railfan

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import cc.samlab.ticket.ui.trainlist.TrainListRow
import cc.samlab.ticket.ui.trainlist.parseTrainListRows
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import org.json.JSONObject

internal data class RoutingTrain(
    val row: TrainListRow,
    val detailTrain: String,
    val highlighted: Boolean,
    val clickable: Boolean,
)

internal data class RoutingGroup(val id: Int, val model: String, val trains: List<RoutingTrain>)

internal class RoutingQueryController(
    private val activity: ComponentActivity,
    private val web: WebView,
    private val worker: ExecutorService,
) {
    var input by mutableStateOf("")
    var submitted by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var invalid by mutableStateOf(false)
        private set
    var groups by mutableStateOf<List<RoutingGroup>>(emptyList())
        private set
    var date by mutableStateOf("")
        private set
    private var requestId = 0
    @Volatile private var closed = false

    fun search() {
        val train = input.trim().uppercase()
        invalid = !Regex("(?:[A-Z]{0,2}\\d{1,5}|0[A-Z]{1,2}\\d{1,5})(?:/[A-Z]*\\d{1,5})*")
            .matches(train)
        if (invalid) return
        input = train
        submitted = train
        date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        groups = emptyList()
        error = false
        loading = true
        val detail = JSONObject().put("requestId", ++requestId)
            .put("train", train).put("date", date)
        web.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-routing-request',{detail:$detail}))",
            null,
        )
    }

    fun clear() {
        requestId++
        input = ""
        submitted = ""
        groups = emptyList()
        loading = false
        error = false
        invalid = false
    }

    fun receive(payload: String) {
        worker.execute {
            val data = runCatching { JSONObject(payload) }.getOrNull() ?: return@execute
            val result = data.optJSONObject("result")
            val parsed = runCatching {
                val routes = result?.optJSONArray("groups")
                List(routes?.length() ?: 0) { index ->
                    val route = routes!!.getJSONObject(index)
                    val rows = route.getJSONArray("rows")
                    val summaries = parseTrainListRows(activity, rows)
                    RoutingGroup(route.getInt("id"), route.getString("model"),
                        summaries.mapIndexed { position, row ->
                            val metadata = rows.getJSONObject(position)
                            RoutingTrain(row, metadata.optString("detailTrain"),
                                metadata.optBoolean("highlighted"), metadata.optBoolean("clickable"))
                        })
                }
            }
            activity.runOnUiThread {
                if (!closed && data.optInt("requestId") == requestId) {
                    error = data.optBoolean("error") || parsed.isFailure || result == null
                    groups = parsed.getOrDefault(emptyList())
                    loading = false
                }
            }
        }
    }

    fun close() { closed = true }
}
