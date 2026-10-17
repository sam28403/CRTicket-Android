package cc.samlab.ticket.ui.monitor

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class NativeTicketMonitorController(
    private val activity: ComponentActivity,
    private val webView: WebView,
) {
    internal var state by mutableStateOf(JSONObject())
        private set
    internal var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    internal var suggestionKey by mutableStateOf("")
        private set
    private var suggestionQuery = ""
    internal val loading get() = state.optBoolean("loading")
    internal val monitoring get() = state.optBoolean("monitoring")
    internal var lastMonitorAt by mutableStateOf("")
        private set

    fun action(action: String, key: String = "", value: Any? = null) {
        val detail = JSONObject().put("action", action).put("key", key).put("value", value)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-monitor-page-request',{detail:$detail}))",
            null,
        )
    }

    internal fun edit(key: String, value: Any) {
        state = JSONObject(state.toString()).put(key, value)
        action("edit", key, value)
    }

    internal fun suggest(key: String, query: String) {
        suggestionKey = key
        suggestionQuery = query
        suggestions = emptyList()
        action("suggest", key, query)
    }

    internal fun toggle(key: String, value: String) {
        val selected = state.optJSONArray(key).strings().toMutableList()
        if (!selected.remove(value)) selected.add(value)
        edit(key, JSONArray(selected))
    }

    fun receive(payload: String) {
        if (payload.length > 8_000_000) return
        val result = runCatching { JSONObject(payload) }.getOrNull() ?: return
        activity.runOnUiThread {
            if (activity.isDestroyed) return@runOnUiThread
            if (result.optString("kind") == "suggestions") {
                if (suggestionKey == result.optString("key") && suggestionQuery == result.optString("query")) {
                    val items = result.optJSONArray("suggestions")
                    suggestions = List(items?.length() ?: 0) { items!!.getJSONObject(it).optString("value") }
                }
            } else {
                state = result
                lastMonitorAt = result.optString("lastMonitorAt")
            }
        }
    }
}

internal fun JSONArray?.strings(): List<String> =
    List(this?.length() ?: 0) { this!!.optString(it) }
