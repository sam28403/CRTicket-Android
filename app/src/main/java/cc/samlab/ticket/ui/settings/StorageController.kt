package cc.samlab.ticket.ui.settings

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.R
import java.util.concurrent.ExecutorService
import org.json.JSONObject

internal class StorageController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    private val repository = StorageRepository(activity)
    var snapshot by mutableStateOf(StorageSnapshot())
        private set
    var busy by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    private var requestId = 0
    private var selected = emptySet<StorageKind>()
    private val timeout = Runnable { fail() }

    fun refresh() = runOperation(emptySet())

    fun clear(kinds: Set<StorageKind>) = runOperation(kinds)

    private fun runOperation(kinds: Set<StorageKind>) {
        if (busy) return
        busy = true
        message = ""
        selected = kinds
        val id = "storage:${++requestId}"
        val action = if (StorageKind.TIMETABLE in kinds) "clear" else "snapshot"
        webView.postDelayed(timeout, 100_000)
        webView.evaluateJavascript(
            "if (window.crticketStorage) {" +
                "window.crticketStorage(${JSONObject.quote(id)},${JSONObject.quote(action)})" +
                "} else { CRTicketAndroid.settingsResult(JSON.stringify({" +
                "id:${JSONObject.quote(id)},success:false})) }",
            null,
        )
    }

    fun receive(result: JSONObject) {
        if (!busy || result.optString("id") != "storage:$requestId") return
        webView.removeCallbacks(timeout)
        if (!result.optBoolean("success")) {
            fail()
            return
        }
        val id = requestId
        val kinds = selected
        val archiveBytes = result.optLong("bytes").coerceAtLeast(0)
        // WebView must clear its own HTTP cache on the UI thread.
        val webResult = runCatching {
            if (StorageKind.WEB in kinds) webView.clearCache(true)
        }
        worker.execute {
            val result = runCatching {
                webResult.getOrThrow()
                kinds.filter { it != StorageKind.WEB && it != StorageKind.TIMETABLE }
                    .forEach(repository::clear)
                repository.scan(archiveBytes)
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || id != requestId) return@runOnUiThread
                result.onSuccess {
                    snapshot = it
                    loaded = true
                    if (kinds.isNotEmpty()) message = activity.getString(R.string.storage_done)
                }.onFailure {
                    message = activity.getString(R.string.settings_operation_failed)
                }
                busy = false
            }
        }
    }

    private fun fail() {
        requestId++
        busy = false
        message = activity.getString(R.string.settings_operation_failed)
    }

    fun close() {
        requestId++
        webView.removeCallbacks(timeout)
    }
}
