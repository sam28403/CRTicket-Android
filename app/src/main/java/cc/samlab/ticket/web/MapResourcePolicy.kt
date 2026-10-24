package cc.samlab.ticket.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MapResourcePolicy {
    const val TILE_ORIGIN = "https://a.tile.openstreetmap.fr https://tile.openstreetmap.org https://a.tile.opentopomap.org"

    fun isTile(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        val path = when (url.host) {
            "a.tile.openstreetmap.fr" -> "^/(hot|osmfr)/\\d{1,2}/\\d+/\\d+\\.png$"
            "tile.openstreetmap.org", "a.tile.opentopomap.org" -> "^/\\d{1,2}/\\d+/\\d+\\.png$"
            else -> return false
        }
        return url.scheme == "https" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null &&
            Regex(path).matches(url.encodedPath)
    }

    fun isTransportResource(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.host in setOf(
                "www.openstreetmap.org", "tile.thunderforest.com",
                "a.tile.thunderforest.com", "b.tile.thunderforest.com",
                "c.tile.thunderforest.com", "api.thunderforest.com",
            )
    }
}
