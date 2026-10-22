package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun BackupContentsDialog(controller: NativeSettingsController) {
    if (!controller.exportSelection && controller.importText == null) return
    val exporting = controller.exportSelection
    AlertDialog(
        onDismissRequest = controller::dismissBackup,
        title = {
            Text(stringResource(
                if (exporting) R.string.settings_export_backup else R.string.settings_import_backup,
            ))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                controller.backupAvailable.forEach { item ->
                    val checked = item in controller.backupSelected
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { controller.toggleBackupContent(item) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = stringResource(item.label),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = controller.backupSelected.isNotEmpty(),
                onClick = controller::confirmBackup,
            ) {
                Text(stringResource(
                    if (exporting) R.string.backup_start_export else R.string.settings_start_import,
                ))
            }
        },
        dismissButton = {
            TextButton(onClick = controller::dismissBackup) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
