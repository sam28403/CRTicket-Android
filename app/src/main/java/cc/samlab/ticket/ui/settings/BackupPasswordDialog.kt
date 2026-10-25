package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun BackupPasswordDialog(controller: NativeSettingsController) {
    val security = controller.backupSecurity
    if (!security.visible) return
    val exporting = security.exporting
    AlertDialog(
        onDismissRequest = controller::dismissBackup,
        title = {
            Text(stringResource(
                if (exporting) R.string.settings_export_backup else R.string.settings_import_backup,
            ))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (exporting) Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.backup_encrypt), Modifier.weight(1f))
                    Switch(
                        checked = security.encrypt,
                        enabled = !controller.busy,
                        onCheckedChange = {
                            security.encrypt = it
                            security.password = ""
                            security.confirmation = ""
                            security.error = ""
                        },
                        modifier = Modifier.testTag("backup-encryption"),
                    )
                }
                if (!exporting || security.encrypt) {
                    OutlinedTextField(
                        value = security.password,
                        onValueChange = { security.password = it.take(1024); security.error = "" },
                        label = { Text(stringResource(R.string.backup_password)) },
                        enabled = !controller.busy,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("backup-password"),
                    )
                    if (exporting) OutlinedTextField(
                        value = security.confirmation,
                        onValueChange = { security.confirmation = it.take(1024); security.error = "" },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        enabled = !controller.busy,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("backup-password-confirm"),
                    )
                }
                if (security.error.isNotBlank()) Text(
                    security.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !controller.busy &&
                    ((exporting && !security.encrypt) || security.password.isNotEmpty()),
                onClick = controller::confirmBackupPassword,
                modifier = Modifier.testTag("backup-password-submit"),
            ) {
                Text(stringResource(
                    if (exporting) R.string.backup_start_export else R.string.action_confirm,
                ))
            }
        },
        dismissButton = {
            TextButton(enabled = !controller.busy, onClick = controller::dismissBackup) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
