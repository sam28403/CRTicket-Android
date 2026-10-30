package cc.samlab.ticket.ui.station

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate

internal class QuizCountsBridge(private val web: WebView) {
    private var sequence = 0
    private val pending = mutableMapOf<Int, Pair<LocalDate, CompletableDeferred<Map<String, Int>?>>>()

    suspend fun query(date: LocalDate): Map<String, Int>? = withContext(Dispatchers.Main) {
        val id = ++sequence
        val result = CompletableDeferred<Map<String, Int>?>()
        pending[id] = date to result
        try {
            val detail = JSONObject().put("quizDate", date.toString()).put("requestId", id)
            web.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('crticket-visited-stations-request',{detail:$detail}))",
                null,
            )
            withTimeoutOrNull(100_000) { result.await() }
        } finally {
            pending.remove(id)
        }
    }

    fun receive(value: JSONObject) {
        val (date, result) = pending.remove(value.optInt("requestId")) ?: return
        val counts = runCatching {
            require(!value.optBoolean("error") && value.getString("date") == date.toString())
            val rows = value.getJSONArray("stations")
            require(rows.length() > 0)
            List(rows.length()) { rows.getJSONObject(it) }.associate {
                it.getString("station") to it.getInt("total").also { total -> require(total > 0) }
            }
        }.getOrNull()
        result.complete(counts)
    }
}
