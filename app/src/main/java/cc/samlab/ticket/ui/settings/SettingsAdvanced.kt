package cc.samlab.ticket.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.RailfanPreferences

@Composable
internal fun SettingsAdvanced() {
    val context = LocalContext.current
    val enabled by RailfanPreferences.rememberEnabled()
    val title = stringResource(R.string.railfan_tools)
    SettingsGroup(stringResource(R.string.settings_advanced)) {
        SettingsRow(
            title = title,
            subtitle = stringResource(R.string.railfan_tools_description),
            index = 0,
            count = 1,
            enabled = true,
            onClick = { RailfanPreferences.setEnabled(context, !enabled) },
        ) {
            SettingsSwitch(enabled, true, title) {
                RailfanPreferences.setEnabled(context, it)
            }
        }
    }
}
