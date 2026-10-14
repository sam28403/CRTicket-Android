package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal val settingsThemeModes = listOf("system" to "跟随系统", "light" to "亮色", "dark" to "暗色")

@Composable
internal fun SettingsSwitch(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            uncheckedThumbColor = colors.outline,
            uncheckedTrackColor = colors.surfaceContainerHighest,
            uncheckedBorderColor = colors.outline,
        ),
        thumbContent = {
            Icon(
                if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (checked) colors.primary else colors.surface,
            )
        },
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
        title = { Text("切换主题") },
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
                        Text(title)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
