package cc.samlab.ticket.ui.history

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryDeleteDialog(controller: NativeHistoryController) {
    val row = controller.ui.deleting ?: return
    val ui = controller.ui
    AlertDialog(
        modifier = Modifier.testTag("history-delete-dialog"),
        onDismissRequest = { if (!ui.busy) ui.deleting = null },
        title = { Text("删除记录") },
        text = {
            Column {
                Text("${row.train}  ${row.from} → ${row.to}\n${row.date}")
                Spacer(Modifier.height(16.dp))
                Text("删除后无法恢复，确定删除这条记录吗？")
                if (ui.error.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(ui.error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = controller::confirmDelete,
                enabled = !ui.busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(if (ui.busy) "正在删除…" else "删除") }
        },
        dismissButton = {
            TextButton(onClick = { ui.deleting = null }, enabled = !ui.busy) { Text("取消") }
        },
    )
}
