package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun HistoryDeleteDialog(controller: NativeHistoryController) {
    val textResources = rememberAppText()
    val row = controller.ui.deleting ?: return
    val ui = controller.ui
    AlertDialog(
        modifier = Modifier.testTag("history-delete-dialog"),
        onDismissRequest = { if (!ui.busy) ui.deleting = null },
        title = { Text(textResources(R.string.history_delete)) },
        text = {
            Column {
                Text("${row.train}  ${row.from} → ${row.to}\n${row.date}")
                Spacer(Modifier.height(16.dp))
                Text(textResources(R.string.history_delete_confirm))
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
            ) { Text(if (ui.busy) textResources(R.string.status_deleting) else textResources(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = { ui.deleting = null }, enabled = !ui.busy) { Text(textResources(R.string.action_cancel)) }
        },
    )
}
