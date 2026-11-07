package cc.samlab.ticket.ui.settings

import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

internal data class TimetableVersion(val id: String, val date: String)

internal class TimetableSettingsController(
    private val activity: ComponentActivity,
    private val web: WebView,
) {
    private val preferences = activity.getSharedPreferences("developer-options", 0)
    private val versions = activity.getSharedPreferences("data-versions", 0)
    private var selectedAutoUpdate by mutableStateOf(preferences.getBoolean("lltAutoUpdate", true))
    val autoUpdate: Boolean
        get() = cc.samlab.ticket.network.NetworkFeatures.enabled && selectedAutoUpdate
    private val autoUpdateListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "lltAutoUpdate") web.post {
            selectedAutoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(autoUpdateListener)
    }

    var version by mutableStateOf(versions.getString("llt", "").orEmpty())
        private set
    var catalog by mutableStateOf(emptyList<TimetableVersion>())
        private set
    var activeId by mutableStateOf("")
        private set
    var officialVersion by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    private var pending: Pair<String, CompletableDeferred<String>>? = null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) {
            busy = false
        } else importFiles(uris)
    }

    internal fun importFile(uri: Uri) {
        if (!busy) importFiles(listOf(uri))
    }

    private fun importFiles(uris: List<Uri>) {
        busy = true
        activity.lifecycleScope.launch {
            var imported = 0
            var duplicates = 0
            var failed = 0
            try {
                for (uri in uris) {
                    when (runOperation { id -> transfer(uri, id) }) {
                        "success" -> imported++
                        "duplicate" -> duplicates++
                        else -> failed++
                    }
                }
                val message = if (uris.size > 1) {
                    activity.getString(R.string.llt_import_summary, imported, duplicates, failed)
                } else {
                    activity.getString(when {
                        imported > 0 -> R.string.llt_import_success
                        duplicates > 0 -> R.string.llt_import_duplicate
                        else -> R.string.llt_import_failed
                    })
                }
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun transfer(uri: Uri, id: String) {
        val bytes = withContext(Dispatchers.IO) {
            readSettingsFile(activity, uri, 16 * 1024 * 1024)
        }
        check(evaluate("window.crticketTimetable?.begin($id, ${bytes.size})") == "true")
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(192 * 1024, bytes.size - offset)
            val chunk = withContext(Dispatchers.Default) {
                Base64.encodeToString(bytes, offset, count, Base64.NO_WRAP)
            }
            check(evaluate("window.crticketTimetable.append($id, ${JSONObject.quote(chunk)})") == "true")
            offset += count
        }
        check(evaluate("Boolean(window.crticketTimetable?.finish($id))") == "true")
    }

    fun select(id: String) {
        if (id == activeId) return
        changeCatalog("select", id, R.string.llt_switch_failed)
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        changeCatalog("delete", id, R.string.llt_delete_failed, onDeleted)
    }

    fun restore(id: String) {
        changeCatalog("restore", id, R.string.llt_restore_failed)
    }

    fun discardUndo(id: String) {
        web.evaluateJavascript("window.crticketTimetable?.discardUndo(${JSONObject.quote(id)})", null)
    }

    private fun changeCatalog(action: String, id: String, failure: Int, onSuccess: () -> Unit = {}) {
        if (autoUpdate || busy) return
        busy = true
        activity.lifecycleScope.launch {
            try {
                val result = runOperation { operation ->
                    val arguments = "$operation, ${JSONObject.quote(id)}"
                    check(evaluate("Boolean(window.crticketTimetable?.$action($arguments))") == "true")
                }
                if (result == "success") onSuccess()
                else Toast.makeText(activity, failure, Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun runOperation(action: suspend (String) -> Unit): String {
        if (!BuildConfig.CI_BUILD) return "failed"
        val operation = "timetable:${java.util.UUID.randomUUID()}"
        val id = JSONObject.quote(operation)
        val result = CompletableDeferred<String>()
        pending = operation to result
        return try {
            withTimeoutOrNull(90_000) {
                action(id)
                result.await()
            } ?: "failed"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "failed"
        } finally {
            pending = null
            web.evaluateJavascript("window.crticketTimetable?.cancel($id)", null)
        }
    }

    fun changeAutoUpdate(value: Boolean) {
        if (!BuildConfig.CI_BUILD || (busy && value) ||
            (value && !cc.samlab.ticket.network.NetworkFeatures.isAllowed())) return
        selectedAutoUpdate = value
        preferences.edit().putBoolean("lltAutoUpdate", value).apply()
        web.evaluateJavascript("window.crticketTimetable?.configure()", null)
    }

    fun refresh() {
        version = versions.getString("llt", "").orEmpty()
        web.evaluateJavascript("window.crticketTimetable?.snapshot(true)", null)
    }

    fun chooseFile() {
        if (!BuildConfig.CI_BUILD || busy) return
        busy = true
        try {
            picker.launch("*/*")
        } catch (_: Exception) {
            busy = false
            Toast.makeText(activity, R.string.llt_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun receive(result: JSONObject) {
        version = versions.getString("llt", "").orEmpty()
        val id = result.optString("id")
        if (id == "timetable:snapshot") {
            val entries = result.optJSONArray("entries") ?: return
            activeId = if (result.isNull("activeId")) "" else result.getString("activeId")
            version = if (result.isNull("version")) "" else result.getString("version")
            officialVersion = if (result.isNull("officialVersion")) "" else result.getString("officialVersion")
            catalog = List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                TimetableVersion(entry.getString("id"), entry.getString("version"))
            }
            return
        }
        pending?.let { (operation, response) ->
            if (id == operation) response.complete(result.optString("status", "failed"))
        }
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(autoUpdateListener)
    }

    private fun finish() {
        refresh()
        busy = false
    }

    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        web.evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
}
