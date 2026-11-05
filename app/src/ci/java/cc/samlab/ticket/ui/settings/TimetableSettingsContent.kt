package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R

@Composable
internal fun TimetableDeveloperGroup(controller: TimetableSettingsController, onImport: () -> Unit) {
    if (!BuildConfig.CI_BUILD) return
    val title = stringResource(R.string.llt_auto_update)
    SettingsGroup(stringResource(R.string.llt_settings_group)) {
        val canUpdate = cc.samlab.ticket.network.NetworkFeatures.enabled && !controller.busy
        Box(Modifier.alpha(if (canUpdate) 1f else 0.38f)) {
            SettingsRow(title, "", 0, 2, canUpdate, {
                controller.changeAutoUpdate(!controller.autoUpdate)
            }) {
                SettingsSwitch(controller.autoUpdate, canUpdate, title, controller::changeAutoUpdate)
            }
        }
        val enabled = !controller.autoUpdate && !controller.busy
        Box(Modifier.alpha(if (enabled) 1f else 0.38f).testTag("llt-import-entry")) {
            SettingsRow(stringResource(R.string.llt_import_title), "", 1, 2, enabled, onImport)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimetableImportContent(controller: TimetableSettingsController, visible: Boolean) {
    if (!BuildConfig.CI_BUILD) return
    LaunchedEffect(visible) { if (visible) controller.refresh() }
    SettingsPanel(rememberScrollState(), "llt-import-content") {
        Button(
            onClick = controller::chooseFile,
            enabled = !controller.autoUpdate && !controller.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)
                .testTag("llt-import-button"),
        ) {
            if (controller.busy) LoadingIndicator(Modifier.size(24.dp))
            else Text(stringResource(R.string.llt_import_button))
        }
        SettingsGroup(stringResource(R.string.llt_current_version)) {
            SettingsRow(
                controller.version.ifEmpty { stringResource(R.string.llt_version_empty) },
                "", 0, 1, false, {}, trailing = {},
            )
        }
    }
}
