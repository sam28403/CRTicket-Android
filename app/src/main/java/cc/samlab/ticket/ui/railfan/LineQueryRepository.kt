package cc.samlab.ticket.ui.railfan

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class LineQueryRepository(context: Context) {
    private val assets = context.assets
    private val folder = File(context.cacheDir, "railway-lines").apply { mkdirs() }

    suspend fun catalog(): List<String> = withContext(Dispatchers.IO) {
        val cached = File(folder, "catalog.html")
        runCatching { LineQueryParser.catalog(cached.readText()) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: assets.open("railway-lines.json").bufferedReader().use {
                val array = JSONArray(it.readText().removePrefix("\uFEFF"))
                List(array.length()) { index -> array.getString(index) }
            }
    }

    suspend fun refreshCatalog(): List<String> = withContext(Dispatchers.IO) {
        val file = File(folder, "catalog.html")
        val html = read("", file) { LineQueryParser.catalog(it).isNotEmpty() }
        LineQueryParser.catalog(html)
    }

    suspend fun stops(line: String): List<RailwayLineStop> = withContext(Dispatchers.IO) {
        // Encoded filenames keep upstream names out of filesystem paths.
        val filename = java.net.URLEncoder.encode(line, "UTF-8") + ".html"
        val html = read(line, File(folder, filename)) { LineQueryParser.stops(it, line).isNotEmpty() }
        LineQueryParser.stops(html, line)
    }

    private suspend fun read(line: String, file: File, valid: (String) -> Boolean): String {
        val cached = runCatching { file.readText() }.getOrNull()?.takeIf(valid)
        if (cached != null && System.currentTimeMillis() - file.lastModified() < TimeUnit.DAYS.toMillis(1)) {
            return cached
        }
        return try {
            val html = download(line)
            if (!valid(html)) throw IOException("Missing railway data")
            runCatching { file.writeText(html) }
            html
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            cached ?: throw error
        }
    }

    private suspend fun download(line: String): String = suspendCancellableCoroutine { task ->
        val url = "https://www.jprailfan.com/tools/stat/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("linename", line).build()
        val call = client.newCall(Request.Builder().url(url).build())
        task.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (task.isActive) task.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val html = it.body.string()
                        if (task.isActive) task.resume(html)
                    } catch (error: Exception) {
                        if (task.isActive) task.resumeWithException(error)
                    }
                }
            }
        })
    }

    private companion object {
        val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    }
}
