package cc.samlab.ticket.ui.settings

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keep an in-progress edit when the window moves between standalone and embedded settings. */
internal class SettingsUiState {
    var dialog by mutableStateOf("")
        private set
    var nameDraft by mutableStateOf<String?>(null)
    var permissionsPage by mutableStateOf(false)
        private set
    var backProgress by mutableStateOf(0f)

    fun openPermissions() {
        dismiss()
        permissionsPage = true
        backProgress = 0f
    }

    fun closePermissions(): Boolean {
        if (!permissionsPage) return false
        permissionsPage = false
        backProgress = 0f
        return true
    }

    fun save(state: Bundle) = state.putBoolean("settingsPermissionsPage", permissionsPage)

    fun restore(state: Bundle?) {
        permissionsPage = state?.getBoolean("settingsPermissionsPage") == true
    }

    fun open(value: String) {
        nameDraft = null
        dialog = value
    }

    fun dismiss() = open("")
}
