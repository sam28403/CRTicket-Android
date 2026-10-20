package cc.samlab.ticket.network

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UpstreamPolicy {
    fun validate(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull()
            ?: throw IllegalArgumentException(appString(R.string.network_error_invalid_url))
        require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
            appString(R.string.network_error_url_format)
        }
        val allowed = when (url.host) {
            "search.12306.cn" -> url.scheme == "https" && url.port == 443 &&
                url.encodedPath == "/search/v1/train/search"
            "kyfw.12306.cn" -> url.scheme == "https" && url.port == 443 && (
                url.encodedPath in setOf(
                    "/otn/queryTrainInfo/query",
                    "/otn/czxx/queryByTrainNo",
                    "/otn/czxx/init",
                    "/otn/czxx/query",
                    "/otn/resources/js/framework/station_name.js",
                    "/otn/leftTicket/init"
                ) || Regex("^/otn/leftTicket/query[A-Za-z]*$").matches(url.encodedPath)
            )
            "down.lltskb.com" -> url.scheme == "http" && url.port == 80 &&
                url.encodedPath in setOf("/android.ver", "/an.db")
            "223.107.87.50" -> url.scheme == "http" && url.port == 8011 &&
                url.encodedPath == "/an.db"
            else -> false
        }
        require(allowed) { appString(R.string.network_error_host_not_allowed) }
        return url
    }
}
