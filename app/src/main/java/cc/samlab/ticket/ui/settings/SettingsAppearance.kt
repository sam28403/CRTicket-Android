package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.AppSwitch
import org.json.JSONObject

internal val settingsThemeModes = listOf(
    "system" to R.string.theme_system,
    "light" to R.string.theme_light,
    "dark" to R.string.theme_dark,
)

@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
) {
    AppSwitch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

internal fun NativeSettingsController.changeTheme(mode: String = state.themeMode, pureBlack: Boolean = state.pureBlack) {
    request("theme", JSONObject().put("mode", mode).put("pureBlack", pureBlack))
}

@Composable
internal fun SettingsThemeDialog(controller: NativeSettingsController, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                settingsThemeModes.forEach { (mode, title) ->
                    val selected = controller.state.themeMode == mode
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected, enabled = !controller.busy, role = Role.RadioButton) {
                                controller.changeTheme(mode = mode)
                                dismiss()
                            }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(stringResource(title))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
