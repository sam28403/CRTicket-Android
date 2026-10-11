package cc.samlab.ticket.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UpstreamPolicy {
    fun validate(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("无效的铁路服务地址")
        require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
            "铁路服务地址格式无效"
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
        require(allowed) { "只允许访问 12306 和路路通数据服务" }
        return url
    }
}
