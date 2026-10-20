package cc.samlab.ticket.ui.settings

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.samlab.ticket.R

/** Keep an in-progress edit when the window moves between standalone and embedded settings. */
internal enum class SettingsPage(@androidx.annotation.StringRes val title: Int) {
    MAIN(R.string.home_settings),
    PERMISSIONS(R.string.settings_permissions),
    ARRIVAL(R.string.settings_arrival),
    LANGUAGE(R.string.settings_language),
}

internal class SettingsUiState {
    var onPageChanged: ((SettingsPage, Boolean) -> Unit)? = null
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

    fun openLanguage() {
        showPage(SettingsPage.LANGUAGE)
    }

    private fun showPage(value: SettingsPage) {
        dismiss()
        page = value
        backProgress = 0f
        onPageChanged?.invoke(value, false)
    }

    fun showDestination(value: SettingsPage) {
        page = value
        backProgress = 0f
    }

    fun closeDetail(): Boolean {
        if (!hasDetail) return false
        page = if (page == SettingsPage.PERMISSIONS) permissionsParent else SettingsPage.MAIN
        permissionsParent = SettingsPage.MAIN
        backProgress = 0f
        onPageChanged?.invoke(page, true)
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
