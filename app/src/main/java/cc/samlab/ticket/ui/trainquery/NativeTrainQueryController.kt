package cc.samlab.ticket.ui.trainquery

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

class NativeTrainQueryController(
    private val activity: ComponentActivity,
    private val webView: WebView,
) {
    internal var state by mutableStateOf(JSONObject())
        private set
    var train by mutableStateOf("")
        private set
    var date by mutableStateOf("")
        private set
    private var closed = false

    fun edit(code: String = train, day: String = date) {
        train = code.uppercase().take(40)
        date = day
        dispatch("edit")
    }

    fun dispatch(action: String) {
        val detail = JSONObject().put("action", action).put("train", train).put("date", date)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-train-query-request',{detail:$detail}))",
            null,
        )
    }

    fun receive(payload: String) {
        if (payload.length > 2_000_000) return
        val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
        activity.runOnUiThread {
            if (!closed) {
                // Form and tracking updates should preserve the map's zoom and selection.
                val previousRoute = state.optJSONObject("route")
                if (previousRoute != null && previousRoute.toString() == data.optJSONObject("route")?.toString()) {
                    data.put("route", previousRoute)
                }
                state = data
                train = data.optString("train")
                date = data.optString("date")
            }
        }
    }

    fun close() { closed = true }
}
