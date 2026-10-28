package cc.samlab.ticket.ui.settings

import android.graphics.Bitmap
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.R
import java.util.concurrent.ExecutorService
import org.json.JSONArray
import org.json.JSONObject

data class SettingsState(
    val username: String = "",
    val avatar: String = "",
    val arrivalEnabled: Boolean = false,
    val arrivalActive: Boolean = false,
    val arrivalSupported: Boolean = false,
    val arrivalMessage: String = "",
    val loaded: Boolean = false,
    val themeMode: String = "system",
    val pureBlack: Boolean = false,
)

class NativeSettingsController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    internal var onMonitorSettings: () -> Unit = {}

    fun openMonitorSettings() = onMonitorSettings()

    internal val storage = StorageController(activity, webView, worker)
    internal val ui = SettingsUiState()
    internal val backupSecurity = BackupSecurityState()
    internal val permissions = PermissionSettingsController(activity)
    var state by mutableStateOf(SettingsState(
        username = activity.getString(R.string.status_loading),
        arrivalMessage = activity.getString(R.string.arrival_loading),
    ))
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var failed by mutableStateOf(false)
        private set
    var cropBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var importText by mutableStateOf<String?>(null)
        private set
    var importCount by mutableStateOf(0)
        private set
    var importIncludesSettings by mutableStateOf(false)
        private set
    internal var exportSelection by mutableStateOf(false)
        private set
    internal var backupAvailable by mutableStateOf(BackupContent.entries.toSet())
        private set
    internal var backupSelected by mutableStateOf(BackupContent.entries.toSet())
        private set
    private var requestId = 0
    private var pendingAction = "snapshot"
    private val unlockTimeout = Runnable {
        requestId++
        fail(activity.getString(R.string.settings_operation_failed))
    }
    private var pickingFile = false
    private var welcomeBackup: android.net.Uri? = null

    private val avatarPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickingFile = false
        if (uri != null) readFile {
            val bytes = readSettingsFile(activity, uri, 10 * 1024 * 1024)
            val bitmap = decodeSettingsAvatar(activity, bytes)
            activity.runOnUiThread { cropBitmap = bitmap }
        }
    }
    private val backupPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickingFile = false
        if (uri != null) importBackup(uri)
    }

    fun restoreWelcomeBackup(uri: android.net.Uri) {
        welcomeBackup = uri
        refresh()
    }

    private fun importBackup(uri: android.net.Uri) {
        readFile {
            val text = readSettingsFile(activity, uri, 28 * 1024 * 1024).toString(Charsets.UTF_8)
            val value = org.json.JSONTokener(text).nextValue()
            if ((value as? JSONObject)?.optString("format") == "CRTicketEncryptedBackup") {
                activity.runOnUiThread {
                    if (!activity.isDestroyed) {
                        backupSecurity.clear()
                        backupSecurity.encryptedText = text
                    }
                }
            } else previewBackup(text)
        }
    }

    private fun previewBackup(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= 20 * 1024 * 1024) {
            activity.getString(R.string.settings_file_too_large)
        }
        val value = org.json.JSONTokener(text).nextValue()
        val records = if (value is JSONArray) value else (value as? JSONObject)?.optJSONArray("tickets")
        requireNotNull(records) { activity.getString(R.string.settings_backup_no_tickets) }
        require(records.length() <= 10000) { activity.getString(R.string.settings_backup_limit) }
        val available = availableBackupContents(value)
        require(available.isNotEmpty()) { activity.getString(R.string.settings_backup_no_tickets) }
        activity.runOnUiThread {
            if (activity.isDestroyed) return@runOnUiThread
            backupSecurity.clear()
            backupAvailable = available
            backupSelected = available
            importText = text
            importCount = records.length()
            importIncludesSettings = (value as? JSONObject)?.optInt("schemaVersion") == 2
        }
    }

    fun refresh() {
        permissions.refresh()
        if (!busy && !pickingFile && !exportSelection && !backupSecurity.visible &&
            cropBitmap == null && importText == null) request("snapshot")
    }

    fun request(action: String, payload: JSONObject = JSONObject()) {
        if (busy) return
        busy = true
        failed = false
        pendingAction = action
        if (action != "snapshot") message = activity.getString(R.string.status_processing)
        val id = (++requestId).toString()
        if (action == "unlockBackup") webView.postDelayed(unlockTimeout, 60_000)
        val quotedId = JSONObject.quote(id)
        val unavailable = JSONObject.quote(activity.getString(R.string.settings_not_ready))
        webView.evaluateJavascript(
            "if (window.crticketSettings) {" +
                "window.crticketSettings($quotedId,${JSONObject.quote(action)},$payload)" +
                "} else { CRTicketAndroid.settingsResult(JSON.stringify({id:$quotedId," +
                "success:false,message:$unavailable})) }",
            null,
        )
    }

    fun receive(result: JSONObject) {
        if (result.optString("id").startsWith("storage:")) {
            storage.receive(result)
            return
        }
        if (result.optString("id") != requestId.toString()) return
        webView.removeCallbacks(unlockTimeout)
        result.optJSONObject("state")?.let { value ->
            val arrival = value.optJSONObject("arrival") ?: JSONObject()
            state = SettingsState(
                username = value.optString("username", activity.getString(R.string.settings_default_user)),
                avatar = value.optString("avatar").takeUnless { it == "null" } ?: "",
                arrivalEnabled = arrival.optBoolean("enabled"),
                arrivalActive = arrival.optBoolean("active"),
                arrivalSupported = arrival.optBoolean("supported"),
                arrivalMessage = arrival.optString("message"),
                loaded = true,
                themeMode = value.optString("mode", "system"),
                pureBlack = value.optBoolean("pureBlack"),
            )
        }
        busy = false
        failed = !result.optBoolean("success")
        message = result.optString("message")
        if (pendingAction == "unlockBackup") {
            if (failed) backupSecurity.error = message
            else readFile { previewBackup(result.getString("backupText")) }
            return
        }
        val alreadyNotified = pendingAction.startsWith("export") &&
            message == activity.getString(R.string.settings_file_saved)
        if ((pendingAction != "snapshot" || failed) && message.isNotBlank() &&
            message != activity.getString(R.string.settings_file_cancelled) && !alreadyNotified) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
        welcomeBackup?.let { uri ->
            welcomeBackup = null
            importBackup(uri)
        }
    }

    fun chooseAvatar() {
        if (!busy) try {
            pickingFile = true
            avatarPicker.launch("image/*")
        } catch (_: Exception) {
            pickingFile = false
            fail(activity.getString(R.string.settings_image_picker_failed))
        }
    }

    fun chooseBackup() {
        if (!busy) try {
            pickingFile = true
            backupPicker.launch("application/json")
        } catch (_: Exception) {
            pickingFile = false
            fail(activity.getString(R.string.settings_file_picker_failed))
        }
    }

    fun dismissCrop() { cropBitmap = null }
    fun dismissImport() { importText = null }

    fun chooseExportBackup() {
        if (busy) return
        backupAvailable = BackupContent.entries.toSet()
        backupSelected = backupAvailable
        exportSelection = true
    }

    internal fun toggleBackupContent(item: BackupContent) {
        backupSelected = if (item in backupSelected) backupSelected - item else backupSelected + item
    }

    fun dismissBackup() {
        if (busy) return
        backupSecurity.clear()
        exportSelection = false
        dismissImport()
    }

    fun confirmBackup() {
        if (backupSelected.isEmpty()) return
        if (!exportSelection) {
            confirmImport()
            return
        }
        exportSelection = false
        backupSecurity.clear()
        backupSecurity.exporting = true
    }

    internal fun confirmBackupPassword() {
        if (busy) return
        val submission = backupSecurity.submit(activity, backupSelected.map { it.id }) ?: return
        request(submission.first, submission.second)
    }

    fun saveAvatar(zoom: Float, x: Float, y: Float) {
        val bitmap = cropBitmap ?: return
        busy = true
        worker.execute {
            try {
                val avatar = cropSettingsAvatar(bitmap, zoom, x, y)
                activity.runOnUiThread {
                    if (!activity.isDestroyed) {
                        busy = false
                        cropBitmap = null
                        request("avatar", JSONObject().put("avatar", avatar))
                    }
                }
            } catch (error: Exception) {
                activity.runOnUiThread { fail(error.message ?: activity.getString(R.string.settings_avatar_save_failed)) }
            }
        }
    }

    fun confirmImport() {
        val text = importText ?: return
        importText = null
        request("import", JSONObject().put("text", text)
            .put("contents", JSONArray(backupSelected.map { it.id })))
    }

    private fun readFile(action: () -> Unit) {
        busy = true
        worker.execute {
            try {
                action()
                activity.runOnUiThread { busy = false }
            } catch (error: Exception) {
                activity.runOnUiThread { fail(error.message ?: activity.getString(R.string.settings_file_read_failed)) }
            }
        }
    }

    private fun fail(text: String) {
        webView.removeCallbacks(unlockTimeout)
        if (activity.isDestroyed) return
        busy = false
        failed = true
        message = text
        if (backupSecurity.visible) backupSecurity.error = text
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    fun close() {
        storage.close()
        webView.removeCallbacks(unlockTimeout)
        backupSecurity.clear()
        importText = null
    }
}
