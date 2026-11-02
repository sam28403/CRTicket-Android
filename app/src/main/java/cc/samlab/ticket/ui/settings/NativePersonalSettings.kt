package cc.samlab.ticket.ui.settings

import org.json.JSONObject

internal fun NativeSettingsController.nativeAction(action: String, payload: JSONObject): Boolean {
    when (action) {
        "theme" -> {
            val mode = payload.optString("mode", state.themeMode)
            if (mode !in listOf("system", "light", "dark")) return true
            val black = payload.optBoolean("pureBlack", state.pureBlack)
            state = state.copy(themeMode = mode, pureBlack = black)
            onTheme("${if (black) "contrast" else "classic"}-$mode")
        }
        "arrival" -> onArrival(payload.optBoolean("enabled"))
        "name", "avatar" -> {
            val key = if (action == "name") "username" else "avatar"
            val value = payload.optString(key).takeUnless { it == "null" } ?: ""
            if (action == "name" && (value.isBlank() || value.length > 64 ||
                value.any { it.code < 32 || it.code == 127 })) return true
            preferences.edit().putString("profile_$key", value).apply()
            state = if (action == "name") state.copy(username = value) else state.copy(avatar = value)
            syncProfile()
        }
        else -> return false
    }
    return true
}

private fun NativeSettingsController.syncProfile() {
    val user = JSONObject().put("id", "device")
        .put("username", state.username).put("avatar", state.avatar)
    webView.evaluateJavascript(
        "localStorage.setItem('crticket-device-owner-v1',${JSONObject.quote(user.toString())});" +
            "window.dispatchEvent(new Event('crticket-profile-changed'))", null,
    )
}

