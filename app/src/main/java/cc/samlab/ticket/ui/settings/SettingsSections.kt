package cc.samlab.ticket.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import org.json.JSONObject

@Composable
internal fun SettingsProfile(
    controller: NativeSettingsController,
    onDialog: (String) -> Unit,
) {
    val textResources = rememberAppText()
    val state = controller.state
    val enabled = state.loaded && !controller.busy
    SettingsGroup(stringResource(R.string.settings_profile), first = true) {
        SettingsRow(
            stringResource(R.string.settings_display_name), state.username,
            0, 2, enabled, { onDialog("name") },
        )
        SettingsRow(
            stringResource(R.string.settings_avatar),
            stringResource(R.string.settings_avatar_description),
            1, 2, enabled, { onDialog("avatar") },
        ) { SettingsAvatar(state.avatar, state.username) }
    }
    SettingsGroup(stringResource(R.string.settings_trip_alerts)) {
        SettingsRow(
            stringResource(R.string.settings_arrival),
            stringResource(R.string.settings_arrival_description),
            0, 2, enabled, controller.ui::openArrival,
        ) {
            SettingsChevron()
            VerticalDivider(Modifier.height(40.dp))
            SettingsSwitch(
                state.arrivalEnabled,
                enabled && state.arrivalSupported,
                stringResource(R.string.settings_arrival_switch),
            ) {
                controller.request("arrival", JSONObject().put("enabled", it))
            }
        }
        SettingsRow(
            stringResource(R.string.settings_sale_reminders), "",
            1, 2, true, controller.ui::openSaleReminders,
        )
    }
    SettingsGroup(textResources(R.string.home_monitor)) {
        SettingsRow(
            textResources(R.string.monitor_settings_title), textResources(R.string.monitor_settings_description),
            0, 1, true, controller::openMonitorSettings,
        )
    }
    SettingsGroup(stringResource(R.string.settings_privacy)) {
        SettingsRow(
            stringResource(R.string.settings_permissions),
            stringResource(R.string.settings_permissions_description),
            0, 1, true,
            {
                controller.permissions.refresh()
                controller.ui.openPermissions()
            },
        )
    }
}

@Composable
internal fun SettingsPreferences(
    controller: NativeSettingsController,
    onDialog: (String) -> Unit,
    first: Boolean = false,
) {
    val state = controller.state
    val enabled = state.loaded && !controller.busy
    SettingsGroup(stringResource(R.string.settings_appearance), first = first) {
        val themeTitle = settingsThemeModes.firstOrNull { it.first == state.themeMode }?.second
            ?: R.string.theme_system
        SettingsRow(
            stringResource(R.string.settings_theme), stringResource(themeTitle),
            0, 3, enabled, { onDialog("theme") },
        )
        SettingsRow(
            stringResource(R.string.settings_pure_black),
            stringResource(R.string.settings_pure_black_description),
            1, 3, enabled, { controller.changeTheme(pureBlack = !state.pureBlack) },
        ) {
            SettingsSwitch(state.pureBlack, enabled, stringResource(R.string.settings_pure_black_switch)) {
                controller.changeTheme(pureBlack = it)
            }
        }
        SettingsRow(
            stringResource(R.string.settings_language), selectedLanguageName(),
            2, 3, true, controller.ui::openLanguage,
        )
    }
    SettingsAdvanced()
    SettingsGroup(stringResource(R.string.station_tab_map)) {
        SettingsRow(
            stringResource(R.string.settings_map), stringResource(rememberMapSource().title),
            0, 1, true, controller.ui::openMap,
        )
    }
    SettingsGroup(stringResource(R.string.settings_backup)) {
        SettingsRow(stringResource(R.string.settings_export_excel), "", 0, 3, enabled,
            { controller.request("exportExcel") })
        SettingsRow(stringResource(R.string.settings_export_backup), "", 1, 3, enabled,
            controller::chooseExportBackup)
        SettingsRow(stringResource(R.string.settings_import_backup), "", 2, 3, enabled,
            controller::chooseBackup)
    }
}

@Composable
internal fun SettingsAvatar(data: String, name: String) {
    val bitmap = remember(data) {
        runCatching {
            val bytes = Base64.decode(data.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (bitmap != null) Image(bitmap, stringResource(R.string.settings_current_avatar), Modifier.clip(CircleShape))
        else Box(contentAlignment = Alignment.Center) {
            Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
