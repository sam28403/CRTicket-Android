package cc.samlab.ticket.ui.railfan

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class VehiclePhoto(val info: VehiclePhotoInfo, val image: ImageBitmap)

internal object VehiclePhotoRepository {
    private const val ORIGIN = "http://www.xiaguanzhan.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private suspend fun bytes(request: Request): ByteArray = suspendCancellableCoroutine { task ->
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
                        val data = it.body.byteStream().use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 8 * 1024 * 1024) {
                                    throw IOException("Photo response too large")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        if (data.size > 8 * 1024 * 1024) throw IOException("Photo response too large")
                        if (task.isActive) task.resume(data)
                    } catch (error: Exception) {
                        if (task.isActive) task.resumeWithException(error)
                    }
                }
            }
        })
    }

    suspend fun load(context: Context, query: VehicleSearch, rows: List<VehicleHistory>): VehiclePhoto? =
        withContext(Dispatchers.IO) {
            val cacheToken = VehiclePhotoCache.token()
            if (query.byTrain) return@withContext null
            val number = Regex("[0-9]{4}$").find(query.keyword)?.value ?: return@withContext null
            val vehicles = rows.map { it.vehicle }
            if (vehicles.flatMap { it.split('+') }.map(VehiclePhotoParser::identity).distinct().size > 1) {
                return@withContext null
            }
            val cached = VehiclePhotoMetadata.read(context, query)
                ?.takeIf { VehiclePhotoParser.select(query.keyword, vehicles, listOf(it)) != null }
            val info = cached ?: findPhoto(query, vehicles, number) ?: return@withContext null
            val data = VehiclePhotoCache.read(context, info.imageUrl)
                ?: bytes(Request.Builder().url(info.imageUrl)
                .header("Referer", info.sourceUrl).header("User-Agent", "CRTicketAndroid").build())
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) {
                    inSampleSize *= 2
                }
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)?.asImageBitmap()
                ?.let {
                    VehiclePhotoCache.write(context, info.imageUrl, data, cacheToken)
                    if (cached == null) VehiclePhotoMetadata.write(context, query, info, cacheToken)
                    VehiclePhoto(info, it)
                }
        }

    private suspend fun findPhoto(
        query: VehicleSearch,
        vehicles: List<String>,
        number: String,
    ): VehiclePhotoInfo? {
        val exact = VehiclePhotoParser.searchKeyword(query.keyword)
        for (keyword in listOfNotNull(exact, number).distinct()) {
            val photos = mutableListOf<VehiclePhotoInfo>()
            for (page in 1..5) {
                val request = Request.Builder()
                    .url("$ORIGIN/soso.asp?keyword=$keyword&page=$page")
                    .header("User-Agent", "CRTicketAndroid").build()
                val html = bytes(request).toString(Charset.forName("GB18030"))
                photos += VehiclePhotoParser.parse(html)
                // A full model and number identifies one vehicle, even with multiple photos.
                if (exact != null) {
                    VehiclePhotoParser.select(query.keyword, vehicles, photos)?.let { return it }
                }
                val pages = Regex("总页数：[\\s\\S]*?<span>([0-9]+)</span>")
                    .find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                if (pages > 5) break
                if (page >= pages) {
                    VehiclePhotoParser.select(query.keyword, vehicles, photos)?.let { return it }
                    break
                }
            }
        }
        return null
    }

}
