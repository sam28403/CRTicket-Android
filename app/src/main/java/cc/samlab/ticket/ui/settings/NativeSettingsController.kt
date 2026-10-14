package cc.samlab.ticket.ui.settings

import android.graphics.Bitmap
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService

data class SettingsState(
    val username: String = "正在读取…",
    val avatar: String = "",
    val arrivalEnabled: Boolean = false,
    val arrivalActive: Boolean = false,
    val arrivalSupported: Boolean = false,
    val arrivalMessage: String = "正在读取到站检测状态…",
    val loaded: Boolean = false,
    val themeMode: String = "system",
    val pureBlack: Boolean = false,
)

class NativeSettingsController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val worker: ExecutorService,
) {
    var state by mutableStateOf(SettingsState())
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
    private var requestId = 0
    private var pendingAction = "snapshot"
    private var pickingFile = false

    private val avatarPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickingFile = false
        if (uri != null) readFile {
            val bytes = readSettingsFile(activity, uri, 10 * 1024 * 1024)
            val bitmap = decodeSettingsAvatar(bytes)
            activity.runOnUiThread { cropBitmap = bitmap }
        }
    }
    private val backupPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickingFile = false
        if (uri != null) readFile {
            val text = readSettingsFile(activity, uri, 20 * 1024 * 1024).toString(Charsets.UTF_8)
            val value = org.json.JSONTokener(text).nextValue()
            val records = if (value is JSONArray) value else (value as? JSONObject)?.optJSONArray("tickets")
            requireNotNull(records) { "备份文件中没有找到车票数据" }
            require(records.length() <= 10000) { "一次最多导入 10000 条车票记录" }
            activity.runOnUiThread {
                importText = text
                importCount = records.length()
            }
        }
    }

    fun refresh() {
        if (!busy && !pickingFile && cropBitmap == null && importText == null) request("snapshot")
    }

    fun request(action: String, payload: JSONObject = JSONObject()) {
        if (busy) return
        busy = true
        failed = false
        pendingAction = action
        if (action != "snapshot") message = "正在处理…"
        val id = (++requestId).toString()
        val quotedId = JSONObject.quote(id)
        webView.evaluateJavascript(
            "if (window.crticketSettings) {" +
                "window.crticketSettings($quotedId,${JSONObject.quote(action)},$payload)" +
                "} else { CRTicketAndroid.settingsResult(JSON.stringify({id:$quotedId," +
                "success:false,message:'设置尚未准备好，请重试'})) }",
            null,
        )
    }

    fun receive(result: JSONObject) {
        if (result.optString("id") != requestId.toString()) return
        result.optJSONObject("state")?.let { value ->
            val arrival = value.optJSONObject("arrival") ?: JSONObject()
            state = SettingsState(
                username = value.optString("username", "默认用户"),
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
        val alreadyNotified = pendingAction.startsWith("export") && message == "文件已保存"
        if ((pendingAction != "snapshot" || failed) && message.isNotBlank() &&
            message != "已取消保存" && !alreadyNotified) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    fun chooseAvatar() {
        if (!busy) try {
            pickingFile = true
            avatarPicker.launch("image/*")
        } catch (_: Exception) {
            pickingFile = false
            fail("无法打开图片选择器")
        }
    }

    fun chooseBackup() {
        if (!busy) try {
            pickingFile = true
            backupPicker.launch("application/json")
        } catch (_: Exception) {
            pickingFile = false
            fail("无法打开文件选择器")
        }
    }

    fun dismissCrop() { cropBitmap = null }
    fun dismissImport() { importText = null }

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
                activity.runOnUiThread { fail(error.message ?: "头像保存失败") }
            }
        }
    }

    fun confirmImport() {
        val text = importText ?: return
        importText = null
        request("import", JSONObject().put("text", text))
    }

    private fun readFile(action: () -> Unit) {
        busy = true
        worker.execute {
            try {
                action()
                activity.runOnUiThread { busy = false }
            } catch (error: Exception) {
                activity.runOnUiThread { fail(error.message ?: "文件读取失败") }
            }
        }
    }

    private fun fail(text: String) {
        if (activity.isDestroyed) return
        busy = false
        failed = true
        message = text
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}
