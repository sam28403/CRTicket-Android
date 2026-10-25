package cc.samlab.ticket

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Independent JCA implementation to exercise the Web Crypto reader with large valid payloads. */
internal fun encryptTestBackup(text: String, password: String): String {
    val random = SecureRandom()
    val salt = ByteArray(16).also(random::nextBytes)
    val iv = ByteArray(12).also(random::nextBytes)
    val spec = PBEKeySpec(password.toCharArray(), salt, 600000, 256)
    val key = try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    try {
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("CRTicketEncryptedBackup:1".toByteArray(Charsets.UTF_8))
        fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return JSONObject().put("format", "CRTicketEncryptedBackup").put("version", 1)
            .put("kdf", JSONObject().put("name", "PBKDF2-HMAC-SHA256")
                .put("iterations", 600000).put("salt", base64(salt)))
            .put("cipher", JSONObject().put("name", "AES-256-GCM").put("iv", base64(iv)))
            .put("data", base64(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))).toString()
    } finally {
        key.fill(0)
    }
}
