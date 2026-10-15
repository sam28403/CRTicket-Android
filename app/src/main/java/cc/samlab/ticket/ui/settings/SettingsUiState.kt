package cc.samlab.ticket.ui.settings

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keep an in-progress edit when the window moves between standalone and embedded settings. */
internal enum class SettingsPage(val title: String) {
    MAIN("设置"),
    PERMISSIONS("权限管理"),
    ARRIVAL("到站检测"),
}

internal class SettingsUiState {
    var dialog by mutableStateOf("")
        private set
    var nameDraft by mutableStateOf<String?>(null)
    var page by mutableStateOf(SettingsPage.MAIN)
        private set
    val hasDetail: Boolean get() = page != SettingsPage.MAIN
    private var permissionsParent = SettingsPage.MAIN
    var backProgress by mutableStateOf(0f)

    fun openPermissions() {
        permissionsParent = if (page == SettingsPage.ARRIVAL) SettingsPage.ARRIVAL else SettingsPage.MAIN
        showPage(SettingsPage.PERMISSIONS)
    }

    fun openArrival() {
        showPage(SettingsPage.ARRIVAL)
    }

    private fun showPage(value: SettingsPage) {
        dismiss()
        page = value
        backProgress = 0f
    }

    fun closeDetail(): Boolean {
        if (!hasDetail) return false
        page = if (page == SettingsPage.PERMISSIONS) permissionsParent else SettingsPage.MAIN
        permissionsParent = SettingsPage.MAIN
        backProgress = 0f
        return true
    }

    fun resetPage() {
        showPage(SettingsPage.MAIN)
        permissionsParent = SettingsPage.MAIN
    }

    fun save(state: Bundle) {
        state.putString("settingsPage", page.name)
        state.putString("settingsPermissionsParent", permissionsParent.name)
    }

    fun restore(state: Bundle?) {
        page = SettingsPage.entries.firstOrNull { it.name == state?.getString("settingsPage") }
            ?: if (state?.getBoolean("settingsPermissionsPage") == true) SettingsPage.PERMISSIONS
            else SettingsPage.MAIN
        permissionsParent = if (state?.getString("settingsPermissionsParent") == SettingsPage.ARRIVAL.name) {
            SettingsPage.ARRIVAL
        } else SettingsPage.MAIN
    }

    fun open(value: String) {
        nameDraft = null
        dialog = value
    }

    fun dismiss() = open("")
}
