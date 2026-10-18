package cc.samlab.ticket.ui.generator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import cc.samlab.ticket.ui.history.*
import org.json.JSONObject

class NativeGeneratorController(
    private val activity: ComponentActivity,
    private val web: WebView,
) {
    private val preferences = activity.getSharedPreferences("ticket-generator", 0)
    internal var draft by mutableStateOf<HistoryDraft?>(null)
        private set
    internal var stations by mutableStateOf<List<HistoryStation>>(emptyList())
        private set
    internal var seats by mutableStateOf<List<HistoryChoice>>(emptyList())
        private set
    internal var themes by mutableStateOf<List<HistoryChoice>>(emptyList())
        private set
    internal var airDisabledSeats by mutableStateOf<Set<String>>(emptySet())
        private set
    internal var preview by mutableStateOf<Bitmap?>(null)
        private set
    internal var showPreview by mutableStateOf(true)
    internal var busy by mutableStateOf(false)
        private set
    internal var error by mutableStateOf("")
        private set
    private var currentJson = ""
    private var pendingOperation: String? = null

    fun savedDraft(): String = preferences.getString("draft", "").orEmpty()

    fun receive(payload: String) {
        if (payload.length > 4 * 1024 * 1024) return
        val value = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val bitmap = value.optString("image").takeIf { it.isNotBlank() }?.let {
            runCatching {
                val bytes = Base64.decode(it.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        activity.runOnUiThread {
            value.optJSONArray("stations")?.let { values ->
                stations = List(values.length()) { index ->
                    val row = values.getJSONArray(index)
                    HistoryStation(row.optString(0), row.optString(1), row.optString(2))
                }
            }
            fun choices(key: String): List<HistoryChoice> {
                val values = value.optJSONArray(key) ?: return emptyList()
                return List(values.length()) { index ->
                    val row = values.getJSONObject(index)
                    HistoryChoice(row.optString("value"), row.optString("label"), row.optBoolean("disabled"))
                }
            }
            if (value.has("seats")) seats = choices("seats")
            if (value.has("themes")) themes = choices("themes")
            value.optJSONArray("airDisabledSeats")?.let { values ->
                airDisabledSeats = List(values.length()) { values.getString(it) }.toSet()
            }
            value.optJSONObject("draft")?.let {
                if (draft == null || value.optBoolean("reset")) {
                    draft = HistoryDraft(null, it)
                    persist(draft!!.json().toString())
                    preview = null
                }
            }
            if (bitmap != null && matchesDraft(value.optString("ticket"))) preview = bitmap
            if (value.optString("operation") == pendingOperation && pendingOperation != null) {
                pendingOperation = null
                busy = false
                error = value.optString("error")
            }
        }
    }

    private fun persist(json: String) {
        currentJson = json
        preferences.edit().putString("draft", json).apply()
    }

    private fun matchesDraft(json: String): Boolean = runCatching {
        val rendered = JSONObject(json)
        val current = JSONObject(currentJson)
        val keys = current.keys().asSequence().toSet()
        keys == rendered.keys().asSequence().toSet() && keys.all {
            current.optString(it) == rendered.optString(it)
        }
    }.getOrDefault(false)

    internal fun update(json: String) {
        persist(json)
        if (!busy) dispatch(JSONObject().put("action", "change").put("ticket", JSONObject(json)))
    }

    internal fun action(action: String) {
        val ticket = draft?.json() ?: return
        if (busy) return
        val operation = java.util.UUID.randomUUID().toString()
        pendingOperation = operation
        busy = true
        error = ""
        persist(ticket.toString())
        dispatch(JSONObject().put("action", action).put("ticket", ticket).put("operation", operation))
    }

    private fun dispatch(detail: JSONObject) {
        web.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-generator-action',{detail:$detail}))",
            null,
        )
    }
}
