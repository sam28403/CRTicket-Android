package cc.samlab.ticket.ui.settings

import android.util.Base64
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

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
    var busy by mutableStateOf(false)
        private set
    private var operation = ""
    private val timeout = Runnable { finish(false) }
    private val picker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            busy = false
        } else importFile(uri)
    }

    internal fun importFile(uri: android.net.Uri) {
        busy = true
        activity.lifecycleScope.launch {
            try {
                check(!autoUpdate && BuildConfig.CI_BUILD)
                val bytes = withContext(Dispatchers.IO) {
                    readSettingsFile(activity, uri, 16 * 1024 * 1024)
                }
                check(bytes.isNotEmpty())
                operation = "timetable:${java.util.UUID.randomUUID()}"
                val id = JSONObject.quote(operation)
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
                web.postDelayed(timeout, 90_000)
                evaluate("window.crticketTimetable.finish($id); true")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finish(false)
            }
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
        web.evaluateJavascript("window.crticketTimetable?.snapshot()", null)
    }

    fun chooseFile() {
        if (!BuildConfig.CI_BUILD || autoUpdate || busy) return
        busy = true
        try {
            picker.launch("*/*")
        } catch (_: Exception) {
            finish(false)
        }
    }

    fun receive(result: JSONObject) {
        version = versions.getString("llt", "").orEmpty()
        if (result.optString("id") == "timetable:snapshot") return
        if (!busy || result.optString("id") != operation) return
        finish(result.optBoolean("success"))
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(autoUpdateListener)
        web.removeCallbacks(timeout)
    }

    private fun finish(success: Boolean) {
        web.removeCallbacks(timeout)
        busy = false
        operation = ""
        version = versions.getString("llt", "").orEmpty()
        Toast.makeText(activity, if (success) R.string.llt_import_success else R.string.llt_import_failed,
            Toast.LENGTH_LONG).show()
    }

    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        web.evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
}
