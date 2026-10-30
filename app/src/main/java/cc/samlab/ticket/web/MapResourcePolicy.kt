package cc.samlab.ticket.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MapResourcePolicy {
    const val TILE_ORIGIN = "https://a.tile.openstreetmap.fr https://tile.openstreetmap.org https://a.tile.opentopomap.org"

    const val RAILWAY_ORIGIN = "https://openrailwaymap.app"

    fun railwayGateway(value: String, source: String): String? {
        val url = value.toHttpUrlOrNull() ?: return null
        val origin = when (source) {
            "openrailway" -> RAILWAY_ORIGIN
            else -> return null
        }
        val prefix = "/railway-map/$source/"
        if (url.scheme != "https" || url.host != "appassets.androidplatform.net" ||
            url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            !url.encodedPath.startsWith(prefix)) return null
        return origin + "/" + url.encodedPath.removePrefix(prefix) +
            (url.encodedQuery?.let { "?$it" } ?: "")
    }

    fun isRailwayResource(value: String, source: String = "openrailway"): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        val origin = when (source) {
            "openrailway" -> url.scheme == "https" && url.port == 443 && url.host == "openrailwaymap.app"
            else -> false
        }
        return origin &&
            url.username.isEmpty() && url.password.isEmpty()
    }

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
