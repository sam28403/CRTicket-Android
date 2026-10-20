package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import org.json.JSONObject

@Composable
internal fun SettingsDialogs(dialog: String, dismiss: () -> Unit, controller: NativeSettingsController) {
    val keyboard = LocalSoftwareKeyboardController.current
    if (dialog == "name") {
        val name = controller.ui.nameDraft ?: controller.state.username
        val valid = name.trim().isNotEmpty() && name.length <= 64 && name.none { it.code < 32 || it.code == 127 }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.settings_edit_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { controller.ui.nameDraft = it.take(64) },
                    label = { Text(stringResource(R.string.settings_display_name)) },
                    supportingText = { Text(stringResource(R.string.settings_name_length, name.length, 64)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    keyboard?.hide()
                    controller.request("name", JSONObject().put("username", name.trim()))
                    dismiss()
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (dialog == "avatar") AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.settings_edit_avatar)) },
        text = {
            Column {
                TextButton(onClick = { dismiss(); controller.chooseAvatar() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_choose_image))
                }
                if (controller.state.avatar.isNotEmpty()) TextButton(
                    onClick = {
                        dismiss()
                        controller.request("avatar", JSONObject().put("avatar", JSONObject.NULL))
                    }, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_clear_avatar)) }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (dialog == "theme") SettingsThemeDialog(controller, dismiss)
    if (controller.importText != null) AlertDialog(
        onDismissRequest = controller::dismissImport,
        title = { Text(stringResource(R.string.settings_import_backup)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_import_confirmation, controller.importCount))
                if (controller.importIncludesSettings) {
                    Text(stringResource(R.string.settings_import_settings_confirmation))
                }
            }
        },
        confirmButton = { TextButton(onClick = controller::confirmImport) { Text(stringResource(R.string.settings_start_import)) } },
        dismissButton = { TextButton(onClick = controller::dismissImport) { Text(stringResource(R.string.action_cancel)) } },
    )
    controller.cropBitmap?.let { AvatarCropDialog(it, controller) }
}
