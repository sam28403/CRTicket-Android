package cc.samlab.ticket.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.settings.*
import org.json.JSONObject

internal data class SearchSetting(
    val key: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

@Composable
internal fun searchSettings(
    controller: NativeSettingsController,
    onNavigate: (String) -> Unit,
): List<SearchSetting> {
    val state = controller.state
    val enabled = state.loaded && !controller.busy
    val theme = settingsThemeModes.firstOrNull { it.first == state.themeMode }?.second
        ?: R.string.theme_system
    val entries = listOf(
        SearchSetting("name", stringResource(R.string.settings_display_name), state.username, enabled) {
            controller.ui.open("name")
        },
        SearchSetting("avatar", stringResource(R.string.settings_avatar),
            stringResource(R.string.settings_avatar_description), enabled) { controller.ui.open("avatar") },
        SearchSetting("arrival", stringResource(R.string.settings_arrival),
            stringResource(R.string.settings_arrival_description), enabled) { onNavigate("/settings/arrival") },
        SearchSetting("monitor", stringResource(R.string.monitor_settings_title),
            stringResource(R.string.monitor_settings_description)) { onNavigate("/monitor/settings") },
        SearchSetting("permissions", stringResource(R.string.settings_permissions),
            stringResource(R.string.settings_permissions_description)) { onNavigate("/settings/permissions") },
        SearchSetting("theme", stringResource(R.string.settings_theme), stringResource(theme), enabled) {
            controller.ui.open("theme")
        },
        SearchSetting("black", stringResource(R.string.settings_pure_black),
            stringResource(R.string.settings_pure_black_description), enabled) {
            controller.changeTheme(pureBlack = !state.pureBlack)
        },
        SearchSetting("language", stringResource(R.string.settings_language), selectedLanguageName()) {
            onNavigate("/settings/language")
        },
        SearchSetting("excel", stringResource(R.string.settings_export_excel), "", enabled) {
            controller.request("exportExcel")
        },
        SearchSetting("export", stringResource(R.string.settings_export_backup), "", enabled) {
            controller.chooseExportBackup()
        },
        SearchSetting("import", stringResource(R.string.settings_import_backup), "", enabled,
            controller::chooseBackup),
        SearchSetting("notifications", stringResource(R.string.permissions_notifications),
            stringResource(if (controller.permissions.state.notifications)
                R.string.permissions_notifications_allowed else R.string.permissions_not_allowed_settings)) {
            controller.permissions.openNotifications()
        },
        SearchSetting("location", stringResource(R.string.permissions_location),
            stringResource(when {
                controller.permissions.state.fineLocation -> R.string.permissions_location_precise
                controller.permissions.state.coarseLocation -> R.string.permissions_location_approximate
                else -> R.string.permissions_not_allowed
            })) { controller.permissions.openLocationPermissions() },
        SearchSetting("background-location", stringResource(R.string.permissions_background_location),
            stringResource(if (controller.permissions.state.backgroundLocation)
                R.string.permissions_background_location_allowed else R.string.permissions_background_location_denied)) {
            controller.permissions.openLocationPermissions()
        },
        SearchSetting("battery", stringResource(R.string.permissions_battery),
            stringResource(if (controller.permissions.state.unrestrictedBattery)
                R.string.permissions_battery_unrestricted else R.string.permissions_battery_optimized)) {
            controller.permissions.openBatteryOptimization()
        },
        SearchSetting("all-permissions", stringResource(R.string.permissions_all),
            stringResource(R.string.permissions_all_description)) { controller.permissions.openAppPermissions() },
    )
    val liveUpdates = controller.permissions.state.promotedNotifications ?: return entries
    return entries + SearchSetting("live-updates", stringResource(R.string.permissions_live_updates),
        stringResource(if (liveUpdates) R.string.permissions_live_updates_allowed
            else R.string.permissions_not_allowed_settings)) { controller.permissions.openPromotedNotifications() }
}

@Composable
internal fun SearchSettingsRow(
    item: SearchSetting,
    controller: NativeSettingsController,
) {
    val state = controller.state
    SettingsRow(item.title, item.subtitle, 0, 1, item.enabled, item.action) {
        when (item.key) {
            "avatar" -> SettingsAvatar(state.avatar, state.username)
            "black" -> SettingsSwitch(state.pureBlack, item.enabled, item.title) {
                controller.changeTheme(pureBlack = it)
            }
            "arrival" -> {
                SettingsChevron()
                VerticalDivider(Modifier.height(40.dp))
                SettingsSwitch(state.arrivalEnabled, item.enabled && state.arrivalSupported, item.title) {
                    controller.request("arrival", JSONObject().put("enabled", it))
                }
            }
            else -> SettingsChevron()
        }
    }
}
