package cc.samlab.ticket.ui.railfan

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal object VehiclePhotoCache {
    private const val LIMIT = 32L * 1024 * 1024
    private var generation = 0L

    fun directory(context: Context) = File(context.cacheDir, "vehicle-photos")

    @Synchronized
    fun token(): Long = generation

    private fun file(context: Context, url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = if (url.startsWith("metadata:")) "json" else "jpg"
        return File(directory(context), "$key.$extension")
    }

    @Synchronized
    fun read(context: Context, url: String): ByteArray? = runCatching {
        file(context, url).takeIf { it.isFile && it.length() in 1..(8L * 1024 * 1024) }
            ?.let { image ->
                image.setLastModified(System.currentTimeMillis())
                image.readBytes()
            }
    }.getOrNull()

    @Synchronized
    fun write(context: Context, url: String, bytes: ByteArray, token: Long) {
        if (token != generation) return
        runCatching {
            val folder = directory(context).apply { mkdirs() }
            file(context, url).writeBytes(bytes)
            val files = folder.listFiles().orEmpty().filter { it.isFile }
            var size = files.sumOf { it.length() }
            files.sortedBy { it.lastModified() }.forEach {
                if (size > LIMIT) {
                    val length = it.length()
                    if (it.delete()) size -= length
                }
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        generation++
        val folder = directory(context)
        if (folder.exists()) check(folder.deleteRecursively())
    }
}
