package cc.samlab.ticket.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** One policy for native clients, background work and WebView feature requests. */
internal object NetworkFeatures {
    private const val KEY = "networkEnabled"
    @Volatile private var allowed = true
    var enabled by mutableStateOf(true)
        private set
    private val calls = ConcurrentHashMap.newKeySet<Call>()

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences("developer-options", 0)
        val value = !BuildConfig.CI_BUILD || preferences.getBoolean(KEY, true)
        if (!value) preferences.edit().putBoolean("lltAutoUpdate", false).apply()
        update(value)
    }

    fun isAllowed(): Boolean = allowed

    fun setEnabled(context: Context, value: Boolean) {
        if (!BuildConfig.CI_BUILD) return
        val editor = context.getSharedPreferences("developer-options", 0).edit().putBoolean(KEY, value)
        if (!value) editor.putBoolean("lltAutoUpdate", false)
        editor.apply()
        update(value)
        if (!value) cc.samlab.ticket.monitor.TicketMonitorService.stop(context)
    }

    private fun update(value: Boolean) {
        allowed = value
        enabled = value
        if (!value) calls.forEach { it.cancel() }
    }

    fun requireEnabled() {
        if (!allowed) throw IOException(appString(R.string.network_features_disabled))
    }

    fun blocksRoute(route: String): Boolean = route.substringBefore('?') in setOf(
        "/monitor", "/monitor/settings", "/railfan/vehicle", "/railfan/lines",
        "/railfan/quiz", "/railfan/quiz/report", "/settings/map",
    )

    val interceptor = Interceptor { chain ->
        requireEnabled()
        val response = chain.proceed(chain.request())
        if (!allowed) {
            response.close()
            requireEnabled()
        }
        response
    }

    val events = EventListener.Factory {
        object : EventListener() {
            override fun callStart(call: Call) {
                calls.add(call)
                if (!allowed) call.cancel()
            }
            override fun callEnd(call: Call) { calls.remove(call) }
            override fun callFailed(call: Call, ioe: IOException) { calls.remove(call) }
        }
    }
}
