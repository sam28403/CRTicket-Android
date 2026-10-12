package cc.samlab.ticket.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MapResourcePolicy {
    const val TILE_ORIGIN = "https://a.tile.openstreetmap.fr"

    fun isTile(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.port == 443 &&
            url.host == "a.tile.openstreetmap.fr" &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null &&
            Regex("^/hot/\\d{1,2}/\\d+/\\d+\\.png$").matches(url.encodedPath)
    }
}
