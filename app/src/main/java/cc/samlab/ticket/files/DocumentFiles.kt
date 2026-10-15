package cc.samlab.ticket.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import cc.samlab.ticket.R
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import org.json.JSONObject

class DocumentFiles(
    private val activity: Activity,
    private val worker: ExecutorService,
    private val callback: (String, String, JSONObject) -> Unit,
    private val toast: (String) -> Unit,
) {
    companion object {
        private const val FILE_REQUEST = 10
        private const val SAVE_REQUEST = 11
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }

    private var chooser: ValueCallback<Array<Uri>>? = null
    private var pendingSave: File? = null
    private var pendingSaveId: String? = null
    private var saving = false

    fun choose(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        closeChooser()
        chooser = callback
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
            val accepted = params.acceptTypes
                .filter { it.contains('/') && !it.contains(';') }.toTypedArray()
            if (accepted.isNotEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, accepted)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            activity.startActivityForResult(intent, FILE_REQUEST)
        } catch (_: Exception) {
            closeChooser()
            toast(activity.getString(R.string.files_picker_failed))
        }
        return true
    }

    fun save(id: String, filename: String, mime: String, data: String) {
        if (saving) {
            reply(id, false, activity.getString(R.string.files_save_pending))
            return
        }
        saving = true
        worker.execute {
            try {
                require(data.length <= MAX_FILE_BYTES * 4 / 3 + 4) { activity.getString(R.string.files_size_limit) }
                val bytes = Base64.decode(data, Base64.DEFAULT)
                require(bytes.size <= MAX_FILE_BYTES) { activity.getString(R.string.files_size_limit) }
                val file = File(activity.cacheDir, "export-${UUID.randomUUID()}.tmp")
                    .apply { writeBytes(bytes) }
                activity.runOnUiThread {
                    pendingSave = file
                    pendingSaveId = id
                    try {
                        val safeName = filename.substringAfterLast('/').substringAfterLast('\\')
                            .replace(Regex("[\\p{Cntrl}]"), "").take(180)
                            .ifEmpty { "CRTicket-export" }
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(mime.substringBefore(';').ifEmpty { "application/octet-stream" })
                            .putExtra(Intent.EXTRA_TITLE, safeName)
                        activity.startActivityForResult(intent, SAVE_REQUEST)
                    } catch (error: Exception) {
                        finishSave(false, error.message ?: activity.getString(R.string.files_saver_failed))
                    }
                }
            } catch (error: Exception) {
                activity.runOnUiThread {
                    saving = false
                    reply(id, false, error.message)
                }
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            FILE_REQUEST -> {
                val result = if (resultCode == Activity.RESULT_OK) {
                    data?.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
                        ?: data?.data?.let { arrayOf(it) }
                } else null
                chooser?.onReceiveValue(result)
                chooser = null
            }
            SAVE_REQUEST -> writeDestination(resultCode, data?.data)
        }
    }

    private fun writeDestination(resultCode: Int, destination: Uri?) {
        if (resultCode != Activity.RESULT_OK || destination == null) {
            finishSave(false, activity.getString(R.string.settings_file_cancelled), true)
            return
        }
        val file = pendingSave ?: run {
            finishSave(false, activity.getString(R.string.files_save_expired))
            return
        }
        worker.execute {
            try {
                activity.contentResolver.openOutputStream(destination, "wt").use { output ->
                    requireNotNull(output) { activity.getString(R.string.files_destination_unwritable) }
                    file.inputStream().use { it.copyTo(output) }
                }
                activity.runOnUiThread { finishSave(true, activity.getString(R.string.settings_file_saved)) }
            } catch (error: Exception) {
                activity.runOnUiThread { finishSave(false, error.message ?: activity.getString(R.string.files_save_failed)) }
            }
        }
    }

    private fun reply(id: String, success: Boolean, message: String?, cancelled: Boolean = false) {
        callback("crticketSaveResult", id, JSONObject()
            .put("success", success)
            .put("cancelled", cancelled)
            .put("message", message))
    }

    private fun finishSave(success: Boolean, message: String, cancelled: Boolean = false) {
        pendingSave?.delete()
        pendingSave = null
        saving = false
        pendingSaveId?.let { reply(it, success, message, cancelled) }
        pendingSaveId = null
        if (!cancelled) toast(message)
    }

    fun restore(saved: Bundle?) {
        pendingSave = saved?.getString("saveFile")?.let { name ->
            File(activity.cacheDir, name).takeIf {
                it.exists() && it.parentFile == activity.cacheDir
            }
        }
        pendingSaveId = saved?.getString("saveId")
        saving = pendingSave != null
    }

    fun saveState(outState: Bundle) {
        outState.putString("saveFile", pendingSave?.name)
        outState.putString("saveId", pendingSaveId)
    }

    fun closeChooser() {
        chooser?.onReceiveValue(null)
        chooser = null
    }
}
