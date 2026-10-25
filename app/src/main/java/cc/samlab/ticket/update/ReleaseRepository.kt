package cc.samlab.ticket.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

private const val API_URL = "https://api.github.com/repos/sam28403/CRTicket-Android/releases"

internal data class AppRelease(
    val tag: String,
    val notes: String,
    val downloadUrl: String?,
)

internal class ReleaseRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun release(tag: String): AppRelease? {
        require(ReleaseVersion.parse(tag) != null)
        val response = get("$API_URL/tags/$tag") ?: return null
        val json = JSONObject(response)
        return if (json.optBoolean("draft")) null else parseRelease(json)
    }

    fun check(previews: Boolean, installed: String): AppRelease? {
        val current = ReleaseVersion.parse(installed) ?: error("Invalid installed version")
        val releases = if (previews) {
            buildList {
                var page = 1
                do {
                    val response = get("$API_URL?per_page=100&page=$page") ?: break
                    val items = JSONArray(response)
                    for (index in 0 until items.length()) add(items.getJSONObject(index))
                    page++
                } while (items.length() == 100 && page <= 10)
            }
        } else {
            listOfNotNull(get("$API_URL/latest")?.let(::JSONObject))
        }
        return selectRelease(releases, previews, current)
    }

    internal fun selectRelease(
        releases: List<JSONObject>,
        previews: Boolean,
        current: ReleaseVersion,
    ): AppRelease? = releases.asSequence()
        .filter { !it.optBoolean("draft") && (previews || !it.optBoolean("prerelease")) }
        .mapNotNull { json ->
            val version = ReleaseVersion.parse(json.optString("tag_name")) ?: return@mapNotNull null
            if (version <= current) null else version to json
        }
        .maxByOrNull { it.first }?.second?.let(::parseRelease)

    private fun get(url: String): String? {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CRTicket-Android")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404 && url.endsWith("/latest")) return@use null
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            response.body.string()
        }
    }

    private fun parseRelease(json: JSONObject): AppRelease {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter {
                it.optString("name").endsWith(".apk", ignoreCase = true) &&
                    !it.optString("name").contains("unsigned", ignoreCase = true) &&
                    it.optString("state") == "uploaded" && it.optLong("size") > 0
            }
        val apk = apks.firstOrNull { it.optString("name").contains("universal", true) }
            ?: apks.singleOrNull()
        val url = apk?.optString("browser_download_url")?.takeIf(::isReleaseDownload)
        return AppRelease(json.getString("tag_name"), json.optString("body"), url)
    }
}

internal fun isReleaseDownload(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "github.com" &&
        uri.path.startsWith("/sam28403/CRTicket-Android/releases/download/")
}.getOrDefault(false)
