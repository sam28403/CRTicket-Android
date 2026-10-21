package cc.samlab.ticket.ui.search

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import org.json.JSONObject

internal class NativeSearchController(
    private val activity: ComponentActivity,
    private val web: WebView,
) {
    var result by mutableStateOf(JSONObject())
        private set
    var loading by mutableStateOf(false)
        private set
    var completedQuery by mutableStateOf("")
        private set
    private var pendingQuery = ""
    private var requestId = 0
    private var closed = false

    fun search(query: String, dispatch: Boolean = true) {
        requestId++
        pendingQuery = query.trim()
        completedQuery = ""
        result = JSONObject()
        loading = query.isNotBlank()
        if (!loading || !dispatch) return
        val detail = JSONObject().put("query", query).put("requestId", requestId)
        web.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-search-request',{detail:$detail}))",
            null,
        )
    }

    fun receive(payload: String) {
        if (payload.length > 20_000_000) return
        val value = runCatching { JSONObject(payload) }.getOrNull() ?: return
        activity.runOnUiThread {
            if (!closed && value.optInt("requestId") == requestId) {
                result = value
                completedQuery = pendingQuery
                loading = false
            }
        }
    }

    fun close() { closed = true }
}
