package cc.samlab.ticket.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.network.UpstreamPolicy
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Only About displays dataset versions; feature screens should not repeat them. */
internal class AboutDataVersions(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("data-versions", 0)
    var llt by mutableStateOf(preferences.getString("llt", "").orEmpty())
        private set
    var loading by mutableStateOf(false)
        private set
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private var call: Call? = null
    private var closed = false
    private var attemptedAt = 0L

    fun refresh() {
        val now = System.currentTimeMillis()
        if (closed || loading || now - attemptedAt < TimeUnit.MINUTES.toMillis(1)) return
        if (llt.isNotEmpty() && now - preferences.getLong("checkedAt", 0) < TimeUnit.HOURS.toMillis(1)) return
        attemptedAt = now
        loading = true
        val request = Request.Builder()
            .url(UpstreamPolicy.validate("http://down.lltskb.com/android.ver"))
            .build()
        call = client.newCall(request).also { pending ->
            pending.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) = finish(null)

                override fun onResponse(call: Call, response: Response) {
                    val version = runCatching {
                        response.use {
                            require(it.isSuccessful)
                            val bytes = checkNotNull(it.body).byteStream().use { stream ->
                                val buffer = ByteArray(8193)
                                var size = 0
                                while (size < buffer.size) {
                                    val count = stream.read(buffer, size, buffer.size - size)
                                    if (count < 0) break
                                    size += count
                                }
                                require(size <= 8192)
                                buffer.copyOf(size)
                            }
                            val manifest = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                                .replace("\"name\": 应用宝\"", "\"name\": \"应用宝\"")
                            JSONObject(manifest).optJSONObject("version")?.optString("data")
                                ?.takeIf { value -> value.matches(Regex("\\d{8}")) }
                        }
                    }.getOrNull()
                    finish(version)
                }
            })
        }
    }

    private fun finish(version: String?) {
        activity.runOnUiThread {
            if (closed) return@runOnUiThread
            loading = false
            if (version != null) {
                llt = version
                preferences.edit().putString("llt", version)
                    .putLong("checkedAt", System.currentTimeMillis()).apply()
            }
        }
    }

    fun close() {
        closed = true
        call?.cancel()
    }
}
