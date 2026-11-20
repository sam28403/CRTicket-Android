package cc.samlab.ticket.ui.travel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

private val travelImages = object : LruCache<HttpUrl, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: HttpUrl, value: Bitmap) = value.allocationByteCount
}
private val imageLocks = Array(8) { Mutex() }

internal suspend fun loadTravelImage(url: HttpUrl?): Result<ImageBitmap> =
    withContext(Dispatchers.IO) {
        if (url == null) return@withContext Result.failure(IOException("Invalid image URL"))
        val lock = imageLocks[Math.floorMod(url.hashCode(), imageLocks.size)]
        lock.withLock {
            travelImages[url]?.let { return@withLock Result.success(it.asImageBitmap()) }
            val bitmap = try {
                try {
                    downloadTravelImage(url)
                } catch (_: IOException) {
                    delay(400)
                    downloadTravelImage(url)
                }
            } catch (error: IOException) {
                Log.w("RailwayTravel", "Could not download a railway travel photo", error)
                return@withLock Result.failure(error)
            }
            travelImages.put(url, bitmap)
            Result.success(bitmap.asImageBitmap())
        }
    }

private fun downloadTravelImage(url: HttpUrl): Bitmap {
    val data = TravelRepository.download(url, 12L * 1024 * 1024)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw IOException("Invalid image")
}
