package cc.samlab.ticket.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.R
import org.json.JSONArray
import org.json.JSONObject

/** Passwords and decrypted previews are transient; never persist them in saved state. */
internal class BackupSecurityState {
    var exporting by mutableStateOf(false)
    var encryptedText by mutableStateOf<String?>(null)
    var encrypt by mutableStateOf(true)
    var password by mutableStateOf("")
    var confirmation by mutableStateOf("")
    var error by mutableStateOf("")
    val visible get() = exporting || encryptedText != null

    fun submit(context: Context, contents: List<String>): Pair<String, JSONObject>? {
        if (exporting && encrypt) {
            if (password.codePointCount(0, password.length) < 8 || password.length > 1024) {
                error = context.getString(R.string.backup_password_invalid)
                return null
            }
            if (password != confirmation) {
                error = context.getString(R.string.backup_password_mismatch)
                return null
            }
        }
        val secret = password
        if (exporting) {
            val encrypted = encrypt
            clear()
            return "exportBackup" to JSONObject()
                .put("contents", JSONArray(contents))
                .put("encryption", if (encrypted) "aes-gcm" else "none")
                .put("password", if (encrypted) secret else "")
        }
        password = ""
        error = ""
        return "unlockBackup" to JSONObject().put("text", encryptedText).put("password", secret)
    }

    fun clear() {
        exporting = false
        encryptedText = null
        encrypt = true
        password = ""
        confirmation = ""
        error = ""
    }
}
