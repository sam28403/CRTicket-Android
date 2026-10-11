package cc.samlab.ticket.network

import android.util.Base64
import android.webkit.CookieManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class PhoneHttp(
    private val worker: ExecutorService,
    private val callback: (String, String, JSONObject) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val calls = ConcurrentHashMap<String, Call>()
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    fun enqueue(id: String, method: String, address: String) {
        active.add(id)
        worker.execute {
            val result = JSONObject()
            try {
                require(method == "GET") { "铁路接口仅支持读取数据" }
                val url = UpstreamPolicy.validate(address)
                require(!cancelled.contains(id)) { "请求已取消" }
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 CRTicketAndroid/3.0")
                    .header("Accept", "application/json,text/html,*/*")
                if (url.host.endsWith("12306.cn")) {
                    val referer = when {
                        url.encodedPath.startsWith("/otn/leftTicket") ->
                            "https://kyfw.12306.cn/otn/leftTicket/init"
                        url.encodedPath in setOf("/otn/czxx/init", "/otn/czxx/query") ->
                            "https://kyfw.12306.cn/otn/czxx/init"
                        else -> "https://kyfw.12306.cn/otn/queryTrainInfo/init"
                    }
                    builder.header("Referer", referer)
                }
                CookieManager.getInstance().getCookie(url.toString())?.let {
                    builder.header("Cookie", it)
                }
                val call = client.newCall(builder.build())
                calls[id] = call
                if (cancelled.contains(id)) call.cancel()
                call.execute().use { response ->
                    response.headers.values("Set-Cookie").forEach {
                        CookieManager.getInstance().setCookie(url.toString(), it)
                    }
                    val binary = url.encodedPath == "/an.db"
                    val maxBytes = if (binary) 16 * 1024 * 1024 else 4 * 1024 * 1024
                    val output = ByteArrayOutputStream()
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= maxBytes) { "铁路服务响应过大" }
                            output.write(buffer, 0, count)
                        }
                    }
                    val bytes = output.toByteArray()
                    result.put("status", response.code)
                    result.put("binary", binary)
                    result.put("contentType", response.header("Content-Type"))
                    if (binary) {
                        // Keep each WebView callback well below Android IPC message limits.
                        var offset = 0
                        while (offset < bytes.size) {
                            require(!cancelled.contains(id)) { "请求已取消" }
                            val length = minOf(384 * 1024, bytes.size - offset)
                            callback("crticketBinaryChunk", id, JSONObject().put(
                                "chunk", Base64.encodeToString(bytes, offset, length, Base64.NO_WRAP)
                            ))
                            offset += length
                        }
                        result.put("body", "")
                    } else {
                        result.put("body", bytes.toString(Charsets.UTF_8))
                    }
                }
            } catch (error: Exception) {
                result.put("status", 503)
                result.put("error", error.message ?: "手机无法连接铁路服务")
                result.put("body", "")
            } finally {
                active.remove(id)
                calls.remove(id)
                cancelled.remove(id)
            }
            callback("crticketApiResult", id, result)
        }
    }

    fun cancel(id: String) {
        if (active.contains(id)) cancelled.add(id)
        calls[id]?.cancel()
    }

    fun close() {
        client.dispatcher.cancelAll()
    }
}
