package cc.samlab.ticket.monitor

import android.webkit.CookieManager
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.network.UpstreamPolicy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TicketMonitorClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .build()

    fun cancel() = client.dispatcher.cancelAll()

    private fun get(address: String): String {
        val url = UpstreamPolicy.validate(address)
        val cookies = CookieManager.getInstance()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 CRTicketAndroid/3.0")
            .header("Referer", "https://kyfw.12306.cn/otn/leftTicket/init")
            .header("Accept", "application/json,text/html,*/*")
        cookies.getCookie(address)?.let { request.header("Cookie", it) }
        return client.newCall(request.build()).execute().use { response ->
            require(response.isSuccessful) { appString(R.string.monitor_error_http, response.code) }
            response.headers.values("Set-Cookie").forEach { cookies.setCookie(address, it) }
            val source = response.body.source()
            require(!source.request(4L * 1024 * 1024 + 1)) { appString(R.string.monitor_error_response_size) }
            source.readUtf8()
        }
    }

    fun query(config: TicketMonitorConfig): JSONArray {
        val origin = "https://kyfw.12306.cn/otn/"
        val html = get("${origin}leftTicket/init")
        var path = Regex("CLeftTicketUrl\\s*=\\s*['\"](leftTicket/query[A-Za-z]*)['\"]")
            .find(html)?.groupValues?.get(1)
            ?: error(appString(R.string.monitor_error_endpoint))
        repeat(2) { attempt ->
            val url = UpstreamPolicy.validate("$origin$path").newBuilder()
                .addQueryParameter("leftTicketDTO.train_date", config.date)
                .addQueryParameter("leftTicketDTO.from_station", config.from)
                .addQueryParameter("leftTicketDTO.to_station", config.to)
                .addQueryParameter("purpose_codes", "ADULT").build()
            val body = JSONObject(get(url.toString()))
            if (body.optBoolean("status")) return parseTrains(body.getJSONObject("data"))
            val redirected = body.optString("c_url")
            require(attempt == 0 && Regex("leftTicket/query[A-Za-z]*").matches(redirected)) {
                appString(R.string.monitor_error_no_data)
            }
            path = redirected
        }
        error(appString(R.string.monitor_error_query))
    }

    companion object {
        fun parseTrains(data: JSONObject): JSONArray {
            val result = data.getJSONArray("result")
            val names = data.getJSONObject("map")
            return JSONArray().apply {
                for (i in 0 until result.length()) {
                    val fields = result.getString(i).split('|')
                    require(fields.size >= 33 && fields[2].isNotEmpty() && fields[3].isNotEmpty()) {
                        appString(R.string.monitor_error_incomplete_train)
                    }
                    put(JSONObject()
                        .put("id", "${fields[2]}-${fields[6]}-${fields[7]}")
                        .put("train", fields[3])
                        .put("from", names.optString(fields[6], fields[6]))
                        .put("to", names.optString(fields[7], fields[7]))
                        .put("departure", fields[8]).put("arrival", fields[9])
                        .put("duration", fields[10]).put("canBuy", fields[11] == "Y")
                        .put("status", fields[1].replace(Regex("<[^>]*>"), ""))
                        .put("seats", JSONObject(TicketSeats.parse(fields))))
                }
            }
        }
    }
}
