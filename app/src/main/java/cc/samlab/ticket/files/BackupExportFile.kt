package cc.samlab.ticket.files

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Stages one backup at a time on the WebView bridge thread. */
internal class BackupExportFile(private val directory: File) {
    private data class Transfer(val id: String, val file: File, val output: FileOutputStream)

    private var current: Transfer? = null
    private var closed = false

    @Synchronized
    fun append(id: String, data: String): Boolean {
        if (closed || current?.id?.let { it != id } == true) return false
        return try {
            val transfer = current ?: open(id).also { current = it }
            transfer.output.write(Base64.decode(data, Base64.DEFAULT))
            true
        } catch (_: IOException) {
            abort(id)
            false
        } catch (_: IllegalArgumentException) {
            abort(id)
            false
        }
    }

    private fun open(id: String): Transfer {
        val file = File.createTempFile("export-", ".tmp", directory)
        return try {
            Transfer(id, file, file.outputStream())
        } catch (error: IOException) {
            file.delete()
            throw error
        }
    }

    @Synchronized
    fun finish(id: String): File? {
        val transfer = current?.takeIf { it.id == id } ?: return null
        current = null
        return try {
            transfer.output.close()
            transfer.file.takeIf { it.isFile }
        } catch (_: IOException) {
            transfer.file.delete()
            null
        }
    }

    @Synchronized
    fun abort(id: String) {
        val transfer = current?.takeIf { it.id == id } ?: return
        current = null
        runCatching { transfer.output.close() }
        transfer.file.delete()
    }

    @Synchronized
    fun close() {
        closed = true
        current?.let { abort(it.id) }
    }
}
