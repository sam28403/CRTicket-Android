package cc.samlab.ticket.ui.travel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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
private const val MAX_IMAGE_BYTES = 12L * 1024 * 1024

internal class TravelImageRefresh {
    val requested: MutableSet<HttpUrl> = ConcurrentHashMap.newKeySet()
}

internal fun clearTravelImageMemory() = travelImages.evictAll()

internal fun travelImageUrl(article: TravelArticle, destination: String): HttpUrl? =
    TravelRepository.articleUrl(article).resolve(destination)?.takeIf { it.isHttps }

internal fun cachedTravelImage(url: HttpUrl?): ImageBitmap? =
    url?.let { travelImages[it]?.asImageBitmap() }

internal suspend fun loadTravelImage(
    context: Context,
    url: HttpUrl?,
    refresh: TravelImageRefresh?,
    download: (HttpUrl, Long) -> ByteArray,
    onlyCached: Boolean = false,
): Result<ImageBitmap> =
    withContext(Dispatchers.IO) {
        if (url == null) return@withContext Result.failure(IOException("Invalid image URL"))
        val lock = imageLocks[Math.floorMod(url.hashCode(), imageLocks.size)]
        lock.withLock {
            val key = "image:$url"
            val token = TravelCache.token()
            val memory = travelImages[url]
            if (onlyCached && memory == null && !TravelCache.contains(context, key)) {
                return@withLock Result.failure(IOException("Image is not cached"))
            }
            val online = refresh != null && url !in refresh.requested
            if (!online && memory != null) return@withLock Result.success(memory.asImageBitmap())
            fun readSaved() = TravelCache.read(context, key)?.let { bytes ->
                runCatching { decodeTravelImage(bytes) }.getOrNull()
            }
            val bitmap = try {
                val saved = if (online) null else readSaved()
                saved ?: run {
                    val data = try {
                        download(url, MAX_IMAGE_BYTES)
                    } catch (_: IOException) {
                        delay(400)
                        download(url, MAX_IMAGE_BYTES)
                    }
                    decodeTravelImage(data).also { TravelCache.write(context, key, data, token) }
                }
            } catch (error: IOException) {
                Log.w("RailwayTravel", "Could not download a railway travel photo", error)
                memory ?: readSaved() ?: run {
                    if (online) refresh.requested.add(url)
                    return@withLock Result.failure(error)
                }
            }
            if (online) refresh.requested.add(url)
            TravelCache.ifCurrent(token) { travelImages.put(url, bitmap) }
            Result.success(bitmap.asImageBitmap())
        }
    }

private fun decodeTravelImage(data: ByteArray): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw IOException("Invalid image")
}
