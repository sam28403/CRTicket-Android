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
import org.json.JSONObject

@Composable
internal fun SettingsDialogs(dialog: String, dismiss: () -> Unit, controller: NativeSettingsController) {
    val keyboard = LocalSoftwareKeyboardController.current
    if (dialog == "name") {
        val name = controller.ui.nameDraft ?: controller.state.username
        val valid = name.trim().isNotEmpty() && name.length <= 64 && name.none { it.code < 32 || it.code == 127 }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("修改显示名称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { controller.ui.nameDraft = it.take(64) },
                    label = { Text("显示名称") },
                    supportingText = { Text("${name.length}/64") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    keyboard?.hide()
                    controller.request("name", JSONObject().put("username", name.trim()))
                    dismiss()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
        )
    }
    if (dialog == "avatar") AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("修改头像") },
        text = {
            Column {
                TextButton(onClick = { dismiss(); controller.chooseAvatar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("选择图片")
                }
                if (controller.state.avatar.isNotEmpty()) TextButton(
                    onClick = {
                        dismiss()
                        controller.request("avatar", JSONObject().put("avatar", JSONObject.NULL))
                    }, modifier = Modifier.fillMaxWidth(),
                ) { Text("清除头像") }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
    if (dialog == "theme") SettingsThemeDialog(controller, dismiss)
    if (controller.importText != null) AlertDialog(
        onDismissRequest = controller::dismissImport,
        title = { Text("导入备份") },
        text = { Text("将检查 ${controller.importCount} 条车票记录，自动略过相同记录。现有记录会保留。") },
        confirmButton = { TextButton(onClick = controller::confirmImport) { Text("开始导入") } },
        dismissButton = { TextButton(onClick = controller::dismissImport) { Text("取消") } },
    )
    controller.cropBitmap?.let { AvatarCropDialog(it, controller) }
}
