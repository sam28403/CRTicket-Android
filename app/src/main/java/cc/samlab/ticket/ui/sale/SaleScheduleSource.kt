package cc.samlab.ticket.ui.sale

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal class SaleScheduleSource(private val context: Context) {
    private val preferences = context.getSharedPreferences("sale-schedule", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private var loaded: Map<String, LocalTime> = emptyMap()
    private var checkedDate = ""
    private var stationHash = ""

    suspend fun load(): Map<String, LocalTime> = withContext(Dispatchers.IO) {
        if (loaded.isNotEmpty()) return@withContext loaded
        val bundled = JSONObject(context.assets.open("sale-times.json").bufferedReader().use { it.readText() })
        stationHash = bundled.getString("stationHash")
        val snapshot = runCatching {
            JSONObject(preferences.getString("snapshot", null) ?: error("No cache")).also {
                require(it.optString("stationHash") == stationHash)
                require(it.getString("date") >= bundled.getString("date"))
                decode(it.getJSONObject("stations"))
                LocalDate.parse(it.getString("date"))
            }
        }.getOrElse { bundled }
        checkedDate = snapshot.getString("date")
        loaded = decode(snapshot.getJSONObject("stations"))
        loaded
    }

    suspend fun refresh(): Map<String, LocalTime> = withContext(Dispatchers.IO) {
        load()
        val today = LocalDate.now(saleZone).toString()
        if (checkedDate == today) return@withContext loaded
        try {
            client.newCall(Request.Builder().url(SOURCE).build()).execute().use { response ->
                check(response.isSuccessful)
                val next = parseSaleAnnouncement(response.body.string())
                val values = JSONObject().apply { next.forEach { (name, time) -> put(name, time.toString()) } }
                val snapshot = JSONObject().put("date", today).put("stations", values)
                    .put("stationHash", stationHash)
                preferences.edit().putString("snapshot", snapshot.toString()).apply()
                loaded = next
                checkedDate = today
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("SaleSchedule", "Keeping last validated sale schedule", failure)
        }
        loaded
    }

    private fun decode(values: JSONObject): Map<String, LocalTime> =
        values.keys().asSequence().associateWith { LocalTime.parse(values.getString(it)) }.also {
            require(it.size >= 2000 && it.values.toSet().size == 41)
        }

    companion object {
        const val SOURCE = "https://www.12306.cn/mormhweb/zxdt/201411/t20141126_2316.html"
    }
}
