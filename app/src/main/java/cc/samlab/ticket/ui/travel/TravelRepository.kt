package cc.samlab.ticket.ui.travel

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import cc.samlab.ticket.network.NetworkFeatures
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal data class TravelArticle(val file: String, val modifiedAt: Instant? = null) {
    val pinned: Boolean get() = file.startsWith('#')
    val title: String get() = file.substringBeforeLast('.').removePrefix("#").trimStart()
}

internal fun sortTravelArticles(articles: List<TravelArticle>): List<TravelArticle> =
    articles.sortedWith(compareByDescending<TravelArticle> { it.pinned }
        .thenByDescending { it.modifiedAt }
        .thenBy { it.file })

internal object TravelRepository {
    private const val REPOSITORY = "sam28403/CRTicket-Android"
    private const val BRANCH = "master"
    private const val MAX_MARKDOWN_BYTES = 1024L * 1024L
    private val contentsUrl = "https://api.github.com/repos/$REPOSITORY/contents/travel".toHttpUrl()
    private val indexUrl = contentsUrl.newBuilder().addQueryParameter("ref", BRANCH).build()
    private val commitsUrl = "https://api.github.com/repos/$REPOSITORY/commits".toHttpUrl()
    private val rawRoot = "https://raw.githubusercontent.com/$REPOSITORY/$BRANCH/travel/".toHttpUrl()
    private val cacheLock = Any()
    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkFeatures.interceptor)
        .eventListenerFactory(NetworkFeatures.events)
        .followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun article(file: String?): TravelArticle? = file
        ?.takeIf { it.length <= 255 && it.endsWith(".md", ignoreCase = true) && it.length > 3 }
        ?.takeUnless { '/' in it || '\\' in it || it.any(Char::isISOControl) }
        ?.let { TravelArticle(it) }

    fun articleUrl(article: TravelArticle): HttpUrl =
        rawRoot.newBuilder().addPathSegment(article.file).build()

    suspend fun articles(context: Context): List<TravelArticle> = coroutineScope {
        val articles = withContext(Dispatchers.IO) {
            val entries = JSONArray(downloadText(indexUrl))
            (0 until entries.length()).mapNotNull { index ->
                entries.getJSONObject(index).takeIf { it.optString("type") == "file" }
                    ?.optString("name")?.let(::article)
            }
        }
        val requests = Semaphore(4)
        val dated = articles.map { article ->
            async { requests.withPermit { article.copy(modifiedAt = lastModified(context, article)) } }
        }.awaitAll()
        sortTravelArticles(dated)
    }

    private suspend fun lastModified(context: Context, article: TravelArticle): Instant? {
        val url = commitsUrl.newBuilder()
            .addQueryParameter("sha", BRANCH)
            .addQueryParameter("path", "travel/${article.file}")
            .addQueryParameter("per_page", "1")
            .build()
        return try {
            cached(context, url) { body ->
                val commit = JSONArray(body).optJSONObject(0)?.optJSONObject("commit")
                commit?.optJSONObject("committer")?.getString("date")?.let(Instant::parse)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markdown(article: TravelArticle): String =
        withContext(Dispatchers.IO) {
            val url = indexUrl.newBuilder().addPathSegment(article.file).build()
            val file = JSONObject(downloadText(url))
            if (file.optString("encoding") != "base64") throw IOException("Unsupported Markdown encoding")
            Base64.decode(file.getString("content"), Base64.DEFAULT).toString(Charsets.UTF_8)
        }

    private fun downloadText(url: HttpUrl): String =
        download(url, MAX_MARKDOWN_BYTES).toString(Charsets.UTF_8)

    private suspend fun <T> cached(context: Context, url: HttpUrl, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val key = MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            val directory = File(context.cacheDir, "travel")
            val file = AtomicFile(File(directory, key))
            val content: String
            val result = try {
                content = downloadText(url)
                parse(content)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return@withContext runCatching {
                    val saved = synchronized(cacheLock) { file.readFully().toString(Charsets.UTF_8) }
                    parse(saved)
                }.getOrElse { throw error }
            }
            runCatching {
                synchronized(cacheLock) {
                    directory.mkdirs()
                    val stream = file.startWrite()
                    try {
                        stream.write(content.toByteArray())
                        file.finishWrite(stream)
                    } catch (error: IOException) {
                        file.failWrite(stream)
                        throw error
                    }
                }
            }
            result
        }

    internal fun download(url: HttpUrl, limit: Long): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "CRTicket-Android").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val source = response.body.source()
            if (source.request(limit + 1)) throw IOException("Response exceeds size limit")
            source.readByteArray()
        }
    }
}
