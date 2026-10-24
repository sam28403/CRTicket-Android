package cc.samlab.ticket.ui.trainquery

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object TrainOperatingDaysRepository {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private data class Cached(val time: Long, val days: TrainOperatingDays)
    private val cache = linkedMapOf<Pair<String, LocalDate>, Cached>()

    suspend fun query(train: String, date: LocalDate): TrainOperatingDays {
        require(Regex("[A-Z]?\\d{1,5}").matches(train))
        val key = train to date
        cache[key]?.takeIf { SystemClock.elapsedRealtime() - it.time < 15 * 60_000 }
            ?.let { return it.days }
        val request = Request.Builder()
            .url("https://mobile.12306.cn/wxxcx/wechat/bigScreen/queryTrainDiagram")
            .post(FormBody.Builder()
                .add("trainCode", train)
                .add("queryDate", date.format(DateTimeFormatter.BASIC_ISO_DATE))
                .build())
            .build()
        val body = fetch(request)
        val days = withContext(Dispatchers.Default) {
            val json = JSONObject(body)
            if (!json.optBoolean("status")) throw IOException("Operating calendar unavailable")
            val rows = json.optJSONObject("data")?.optJSONArray("running_list")
                ?: throw IOException("Missing operating calendar")
            trainOperatingDays(List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                row.optString("date") to row.optString("flag")
            }).also {
                if (it.dates.isEmpty()) throw IOException("Empty operating calendar")
            }
        }
        cache[key] = Cached(SystemClock.elapsedRealtime(), days)
        while (cache.size > 16) cache.remove(cache.keys.first())
        return days
    }

    private suspend fun fetch(request: Request): String = suspendCancellableCoroutine { task ->
        val call = client.newCall(request)
        task.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (task.isActive) task.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val body = it.body.string()
                        if (task.isActive) task.resume(body)
                    } catch (error: Exception) {
                        if (task.isActive) task.resumeWithException(error)
                    }
                }
            }
        })
    }
}
