package cc.samlab.ticket.ui.railfan

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object VehicleQueryRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val images = LruCache<String, ImageBitmap>(32)

    private suspend fun bytes(url: String): ByteArray = suspendCancellableCoroutine { task ->
        val call = client.newCall(Request.Builder().url(url)
            .header("User-Agent", "CRTicketAndroid")
            .header("Referer", "https://www.china-emu.cn/Trains/ALL/")
            .build())
        task.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (task.isActive) task.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val data = it.body.bytes()
                        if (task.isActive) task.resume(data)
                    } catch (error: Exception) {
                        if (task.isActive) task.resumeWithException(error)
                    }
                }
            }
        })
    }

    suspend fun search(query: VehicleSearch): List<VehicleHistory> = withContext(Dispatchers.IO) {
        val rows = JSONArray(bytes("https://api.rail.re/${query.path}").toString(Charsets.UTF_8))
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            VehicleHistory(row.getString("emu_no"), row.getString("train_no"), row.getString("date"))
        }.distinct().sortedByDescending { it.date }
    }

    suspend fun image(
        context: Context,
        model: String,
        vehicle: String = "",
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val preferred = VehicleArtworkOverrides.load(context).resolve(vehicle) ?: specialVehicleImage(vehicle)
        val fallback = defaultImagePath(context, model)
        loadWithFallback(preferred, fallback)
    }

    suspend fun carouselImage(
        context: Context,
        name: String,
        preferred: String?,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val model = Regex("^[A-Za-z][A-Za-z0-9-]*").find(name)?.value.orEmpty()
            .uppercase().replace("-", "")
        loadWithFallback(preferred, defaultImagePath(context, model))
    }

    private fun defaultImagePath(context: Context, model: String): String? {
        val catalog = context.assets.open("vehicle-model-images.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val key = vehicleImageModel(model)
        val path = catalog.optString(key).takeIf { it.isNotBlank() }
        if (path != null) return path
        val base = catalog.keys().asSequence().filter { key.startsWith(it) }
            .maxByOrNull { it.length } ?: return null
        return catalog.optString(base).takeIf { it.isNotBlank() }
    }

    private suspend fun loadWithFallback(preferred: String?, fallback: String?): ImageBitmap? {
        for (path in listOfNotNull(preferred, fallback).distinct()) {
            val image = try {
                imagePath(path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            if (image != null) return image
        }
        return null
    }

    suspend fun imagePath(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!path.startsWith("/assets/imgs/Trains/Cute/") || path.contains("..")) return@withContext null
        images.get(path)?.let { return@withContext it }
        val data = bytes("https://www.china-emu.cn$path")
        BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()?.also {
            images.put(path, it)
        }
    }
}
