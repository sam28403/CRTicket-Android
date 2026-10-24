package cc.samlab.ticket.tracking

import android.webkit.CookieManager
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Both public query methods are read-only, including their form POSTs. */
internal object TrainDelayRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val stations = Executors.newFixedThreadPool(4)
    private val zone = ZoneId.of("Asia/Shanghai")
    private const val primary = "https://kyfw.12306.cn/otn/zwdch/query"
    private const val fallback = "https://mobile.12306.cn/wxxcx/wechat/main/travelServiceQrcodeTrainInfo"

    private fun post(url: String, fields: Map<String, String>): String {
        val cookies = CookieManager.getInstance()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://kyfw.12306.cn/otn/zwdch/init")
            .post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        cookies.getCookie(url)?.let { request.header("Cookie", it) }
        return client.newCall(request.build()).execute().use { response ->
            require(response.isSuccessful)
            response.headers.values("Set-Cookie").forEach { cookies.setCookie(url, it) }
            val body = response.body.string()
            require(body.length <= 2_000_000)
            body
        }
    }

    private fun initSession() {
        runCatching {
            val url = "https://kyfw.12306.cn/otn/zwdch/init"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                response.headers.values("Set-Cookie").forEach {
                    CookieManager.getInstance().setCookie(url, it)
                }
            }
        }
    }

    fun query(payload: String): JSONObject {
        require(payload.length <= 200_000)
        val data = JSONObject(payload)
        data.optJSONArray("scheduledStops")?.let {
            data.put("stops", org.json.JSONArray(it.toString()))
        }
        val stops = data.getJSONArray("stops")
        data.put("scheduledStops", org.json.JSONArray(stops.toString()))
        require(stops.length() in 2..200)
        val date = LocalDate.parse(data.getString("date"))
        val today = LocalDate.now(zone)
        val offsets = Array(stops.length()) { arrayOfNulls<Int>(2) }
        // zwdch has no date parameter. Never apply today's prediction to another day.
        if ((0 until stops.length()).any { index ->
                val row = stops.getJSONObject(index)
                date.plusDays(row.optLong("arrivalDay")) == today
            }) {
            initSession()
            val jobs = (0 until stops.length()).flatMap { index ->
                val row = stops.getJSONObject(index)
                listOf("arrival", "departure").mapIndexedNotNull { direction, key ->
                    if (row.isNull(key)) return@mapIndexedNotNull null
                    val day = row.optInt(if (direction == 0) "arrivalDay" else "departureDay",
                        row.optInt("arrivalDay"))
                    if (date.plusDays(day.toLong()) != today) return@mapIndexedNotNull null
                    Callable {
                        offsets[index][direction] = runCatching {
                            val station = row.getString("station")
                            val code = row.optString("train", data.getString("train"))
                            val body = post(primary, mapOf(
                                "cc" to code, "cz" to station, "cxlx" to direction.toString(),
                                "czEn" to station.toByteArray(Charsets.UTF_8).joinToString("") {
                                    "-%02X".format(it.toInt() and 255)
                                },
                            ))
                            val response = JSONObject(body)
                            require(response.optBoolean("status"))
                            val message = response.optJSONObject("data")?.optString("message").orEmpty()
                            TrainDelayTimes.predictionOffset(row.getString(key), message)
                        }.getOrNull()
                    }
                }
            }
            stations.invokeAll(jobs).forEach { runCatching { it.get() } }
        }
        if (offsets.indices.any { index ->
                val row = stops.getJSONObject(index)
                listOf("arrival", "departure").withIndex().any { (direction, key) ->
                    !row.isNull(key) && offsets[index][direction] == null
                }
            }) {
            runCatching {
                val codes = (0 until stops.length()).map { stops.getJSONObject(it).optString("train") }
                    .plus(data.getString("train")).filter { it.matches(Regex("[A-Z]?\\d{1,5}")) }.distinct()
                for (code in codes) {
                    val response = runCatching {
                        JSONObject(post(fallback, mapOf(
                            "trainCode" to code, "startDay" to date.toString().replace("-", ""),
                        )))
                    }.getOrNull() ?: continue
                    val rows = response.optJSONObject("data")?.optJSONObject("trainDetail")
                        ?.optJSONArray("stopTime") ?: continue
                    for (i in 0 until rows.length()) {
                        val item = rows.getJSONObject(i)
                        val matches = (0 until stops.length()).filter {
                            stops.getJSONObject(it).optString("station") == item.optString("stationName")
                        }
                        if (matches.size != 1) continue
                        val index = matches.single()
                        val row = stops.getJSONObject(index)
                        val itemDate = item.optString("trainDate").replace("-", "").take(8)
                        val expectedDate = date.plusDays(row.optLong("arrivalDay")).toString().replace("-", "")
                        if (itemDate != expectedDate) continue
                        val offset = TrainDelayTimes.fallbackOffset(
                            item.optString("ticketDelay"), item.optString("ticketEarly"),
                        ) ?: continue
                        listOf("arrival", "departure").forEachIndexed { direction, key ->
                            if (!row.isNull(key) && offsets[index][direction] == null) offsets[index][direction] = offset
                        }
                    }
                }
            }
        }
        var available = false
        for (index in 0 until stops.length()) {
            val row = stops.getJSONObject(index)
            val originalArrivalDay = row.optInt("arrivalDay")
            val originalDepartureDay = row.optInt("departureDay", originalArrivalDay +
                if (!row.isNull("arrival") && !row.isNull("departure") &&
                    row.getString("departure") < row.getString("arrival")) 1 else 0)
            listOf("arrival", "departure").forEachIndexed { direction, key ->
                val offset = offsets[index][direction] ?: return@forEachIndexed
                available = true
                val corrected = TrainDelayTimes.corrected(date.toString(),
                    if (direction == 0) originalArrivalDay else originalDepartureDay, row.getString(key), offset)
                row.put("${key}Offset", offset)
                row.put(key, corrected.toLocalTime().toString().take(5))
                row.put("${key}Day", ChronoUnit.DAYS.between(date, corrected.toLocalDate()))
            }
            val stay = if (row.isNull("stay")) null else row.optInt("stay")
            row.put("stay", TrainDelayTimes.stay(stay, offsets[index][0], offsets[index][1]) ?: JSONObject.NULL)
        }
        return data.put("delayAvailable", available).put("delayEnabled", available)
    }
}
