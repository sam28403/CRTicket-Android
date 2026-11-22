package cc.samlab.ticket.ui.travel

import android.content.Context
import android.util.Base64
import cc.samlab.ticket.network.NetworkFeatures
import java.io.IOException
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

    suspend fun articles(context: Context, refresh: Boolean): List<TravelArticle> = coroutineScope {
        val articles = withContext(Dispatchers.IO) {
            cachedText(context, indexUrl, refresh) { body ->
                val entries = JSONArray(body)
                (0 until entries.length()).mapNotNull { index ->
                    entries.getJSONObject(index).takeIf { it.optString("type") == "file" }
                        ?.optString("name")?.let(::article)
                }
            }
        }
        val requests = Semaphore(4)
        val dated = articles.map { article ->
            async { requests.withPermit { article.copy(modifiedAt = lastModified(context, article, refresh)) } }
        }.awaitAll()
        sortTravelArticles(dated)
    }

    private suspend fun lastModified(context: Context, article: TravelArticle, refresh: Boolean): Instant? {
        val url = commitsUrl.newBuilder()
            .addQueryParameter("sha", BRANCH)
            .addQueryParameter("path", "travel/${article.file}")
            .addQueryParameter("per_page", "1")
            .build()
        return try {
            withContext(Dispatchers.IO) {
                val entries = cachedText(context, url, refresh, ::JSONArray)
                val commit = entries.optJSONObject(0)?.optJSONObject("commit")
                commit?.optJSONObject("committer")?.getString("date")?.let(Instant::parse)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markdown(context: Context, article: TravelArticle, refresh: Boolean): String =
        withContext(Dispatchers.IO) {
            val url = indexUrl.newBuilder().addPathSegment(article.file).build()
            cachedText(context, url, refresh) { body ->
                val file = JSONObject(body)
                if (file.optString("encoding") != "base64") throw IOException("Unsupported Markdown encoding")
                Base64.decode(file.getString("content"), Base64.DEFAULT).toString(Charsets.UTF_8)
            }
        }

    private fun <T : Any> cachedText(
        context: Context,
        url: HttpUrl,
        refresh: Boolean,
        parse: (String) -> T,
    ): T = TravelCache.get(context, url.toString(), refresh, { download(url, MAX_MARKDOWN_BYTES) }) {
        parse(it.toString(Charsets.UTF_8))
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
