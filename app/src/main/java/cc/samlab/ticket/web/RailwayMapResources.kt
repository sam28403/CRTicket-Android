package cc.samlab.ticket.web

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Official public map data only; no cookies, credentials or native bridge. */
internal object RailwayMapResources {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun load(url: String, source: String): WebResourceResponse {
        if (!MapResourcePolicy.isRailwayResource(url, source)) return failure()
        return runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "CRTicketAndroid/${cc.samlab.ticket.BuildConfig.VERSION_NAME}").header("Referer", LocalAssetClient.ORIGIN + "/").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("CRTicketMap", "Resource HTTP ${response.code}: $url")
                    return failure()
                }
                val type = response.header("Content-Type").orEmpty().lowercase()
                if (type.contains("javascript") || type.contains("text/html")) {
                    android.util.Log.w("CRTicketMap", "Resource rejected content type $type: $url")
                    return failure()
                }
                val output = ByteArrayOutputStream()
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 8 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                }
                WebResourceResponse(
                    response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream",
                    "UTF-8", 200, "OK",
                    mapOf(
                        "Access-Control-Allow-Origin" to LocalAssetClient.ORIGIN,
                        "Cache-Control" to (response.header("Cache-Control") ?: "no-cache"),
                    ),
                    ByteArrayInputStream(output.toByteArray()),
                )
            }
        }.getOrElse {
            android.util.Log.w("CRTicketMap", "Resource ${it.javaClass.simpleName}: ${it.message}: $url")
            failure()
        }
    }

    private fun failure() = WebResourceResponse(
        "text/plain", "UTF-8", 502, "Map resource unavailable",
        mapOf("Access-Control-Allow-Origin" to LocalAssetClient.ORIGIN),
        ByteArrayInputStream(ByteArray(0)),
    )
}
