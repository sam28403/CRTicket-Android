package cc.samlab.ticket.ui.settings

import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.history.HistoryRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

internal class DebugTicketsController(
    private val activity: ComponentActivity,
    private val web: WebView,
) {
    var date by mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString())
    var count by mutableStateOf("1")
    var busy by mutableStateOf(false)
        private set
    var rows by mutableStateOf<List<HistoryRecord>>(emptyList())
        private set

    fun generate() {
        if (busy) return
        val amount = count.toIntOrNull()
        if (amount == null || amount !in 1..1000) {
            toast(false)
            return
        }
        busy = true
        rows = emptyList()
        activity.lifecycleScope.launch {
            try {
                val operation = java.util.UUID.randomUUID().toString()
                val started = evaluate("""
                    (() => {
                        if (!window.crticketDebugGenerate) return false;
                        window.crticketDebugGenerate(${JSONObject.quote(operation)},
                            ${JSONObject.quote(date)}, $amount);
                        return true;
                    })()
                """.trimIndent())
                check(started == "true")
                var result: JSONObject
                while (true) {
                    delay(300)
                    result = runCatching {
                        JSONObject(evaluate("window.crticketDebugResult"))
                    }.getOrDefault(JSONObject())
                    if (result.optString("operation") == operation) break
                }
                check(result.optBoolean("success"))
                val tickets = result.getJSONArray("tickets")
                rows = List(tickets.length()) { index ->
                    val row = tickets.getJSONObject(index)
                    HistoryRecord(
                        row.getString("id"), row.getString("travel_date"), row.getString("departure_time"),
                        row.getString("train_no"), row.getString("departure_station"), row.getString("arrival_station"),
                        row.getString("seat_type"), row.getString("seat_no"), row.getString("ticket_number"),
                        row.getDouble("price"), row.optInt("use_credit") == 1,
                    )
                }
                web.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('crticket-history-action'," +
                        "{detail:{action:'refresh'}}))",
                    null,
                )
                toast(true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(false)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        web.evaluateJavascript(script) { value ->
            if (continuation.isActive) continuation.resume(value)
        }
    }

    private fun toast(success: Boolean) {
        val message = if (success) activity.getString(R.string.debug_tickets_success, rows.size)
            else activity.getString(R.string.debug_tickets_failed)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
