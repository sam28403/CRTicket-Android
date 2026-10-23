package cc.samlab.ticket.ui.sale

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SaleTimeRepository(context: Context) {
    private val preferences = context.getSharedPreferences("sale-time-query", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val cache = mutableMapOf<String, List<SaleTime>>()
    private var cacheDate = LocalDate.now(saleZone)
    var sameCity: Boolean
        get() = preferences.getBoolean("sameCity", true)
        set(value) { preferences.edit().putBoolean("sameCity", value).apply() }

    fun history(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        List(array.length()) { array.getString(it) }.distinct().take(10)
    }.getOrDefault(emptyList())

    fun record(name: String): List<String> = (listOf(name) + history()).distinct().take(10).also {
        preferences.edit().putString("history", JSONArray(it).toString()).apply()
    }

    fun clearHistory() { preferences.edit().remove("history").apply() }

    suspend fun search(
        name: String,
        stations: List<SaleStation>,
        schedule: Map<String, LocalTime>,
    ): List<SaleTime> {
        if (name in schedule) {
            val city = stations.firstOrNull { it.name == name }?.city.orEmpty()
            return stations.asSequence().filter { it.name == name || (city.isNotBlank() && it.city == city) }
                .mapNotNull { station -> schedule[station.name]?.let { SaleTime(station.name, it) } }
                .toList()
        }
        return query(name).map { it.copy(time = schedule[it.name] ?: it.time) }
    }

    suspend fun query(name: String): List<SaleTime> {
        val date = LocalDate.now(saleZone)
        if (cacheDate != date) { cache.clear(); cacheDate = date }
        cache[name]?.let { return it }
        val url = "https://mobile.12306.cn/weixin/wxcore/queryQssj".toHttpUrl()
            .newBuilder().addQueryParameter("stationName", name).build()
        val body = suspendCancellableCoroutine<String> { task ->
            val call = client.newCall(Request.Builder().url(url).build())
            task.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (task.isActive) task.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            val data = it.body.string()
                            if (task.isActive) task.resume(data)
                        } catch (e: Exception) {
                            if (task.isActive) task.resumeWithException(e)
                        }
                    }
                }
            })
        }
        val rows = withContext(Dispatchers.Default) {
            val json = JSONObject(body)
            if (!json.optBoolean("status") || json.optInt("httpstatus") != 200) {
                throw IOException("Invalid sale time response")
            }
            val data = json.getJSONArray("data")
            List(data.length()) { index ->
                val row = data.getJSONObject(index)
                SaleTime(row.getString("name"), LocalTime.parse(row.getString("value")))
            }.distinctBy { it.name }
        }
        cache[name] = rows
        rows.forEach { cache[it.name] = rows }
        return rows
    }
}
