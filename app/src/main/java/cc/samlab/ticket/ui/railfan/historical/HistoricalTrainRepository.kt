package cc.samlab.ticket.ui.railfan.historical

import android.content.Context
import cc.samlab.ticket.network.NetworkFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class HistoricalTrainRepository(context: Context) {
    private val preferences = context.getSharedPreferences("historical-timetables", Context.MODE_PRIVATE)

    suspend fun cachedVersions(): List<String> = withContext(Dispatchers.IO) {
        try {
            parseHistoricalVersions(JSONArray(preferences.getString("versions", "[]")))
        } catch (_: JSONException) {
            emptyList()
        }
    }

    suspend fun refreshVersions(): List<String> {
        val body = fetch(base.newBuilder().addPathSegment("versions").build())
        return withContext(Dispatchers.IO) {
            val versions = parseHistoricalVersions(JSONArray(body))
            if (versions.isEmpty()) throw IOException("Missing timetable versions")
            preferences.edit().putString("versions", JSONArray(versions).toString()).apply()
            versions
        }
    }

    suspend fun query(train: String, version: String): HistoricalTimetable {
        val url = base.newBuilder()
            .addQueryParameter("trainNumber", train)
            .addQueryParameter("version", version)
            .build()
        val body = fetch(url)
        return withContext(Dispatchers.Default) { parseHistoricalTimetable(JSONObject(body), train) }
    }

    private suspend fun fetch(url: HttpUrl): String = suspendCancellableCoroutine { task ->
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
                        val body = it.body.string()
                        if (task.isActive) task.resume(body)
                    } catch (error: Exception) {
                        if (task.isActive) task.resumeWithException(error)
                    }
                }
            }
        })
    }

    private companion object {
        val base = "https://api.raillog.top/api/train-timetables".toHttpUrl()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkFeatures.interceptor)
            .eventListenerFactory(NetworkFeatures.events)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
