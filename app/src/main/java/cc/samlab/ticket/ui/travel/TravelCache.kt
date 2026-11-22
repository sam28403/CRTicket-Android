package cc.samlab.ticket.ui.travel

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

internal object TravelCache {
    private var generation = 0L

    fun directory(context: Context) = File(context.cacheDir, "travel")

    private fun file(context: Context, key: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory(context), hash))
    }

    @Synchronized
    fun token(): Long = generation

    @Synchronized
    fun ifCurrent(token: Long, action: () -> Unit) {
        if (token == generation) action()
    }

    @Synchronized
    fun contains(context: Context, key: String): Boolean = file(context, key).baseFile.isFile

    @Synchronized
    fun read(context: Context, key: String): ByteArray? = runCatching {
        file(context, key).readFully()
    }.getOrNull()

    @Synchronized
    fun write(context: Context, key: String, bytes: ByteArray, token: Long) {
        if (token != generation) return
        runCatching {
            val file = file(context, key)
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        generation++
        clearTravelImageMemory()
        val folder = directory(context)
        if (folder.exists()) check(folder.deleteRecursively())
    }

    fun <T : Any> get(
        context: Context,
        key: String,
        refresh: Boolean,
        download: () -> ByteArray,
        parse: (ByteArray) -> T,
    ): T {
        val token = token()
        fun saved(): T? = read(context, key)?.let { bytes -> runCatching { parse(bytes) }.getOrNull() }
        if (!refresh) saved()?.let { return it }
        return try {
            val bytes = download()
            parse(bytes).also { write(context, key, bytes, token) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            saved() ?: throw error
        }
    }
}
