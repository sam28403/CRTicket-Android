package cc.samlab.ticket.ui.sale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.sale.SaleReminder
import java.time.Instant

@Composable
internal fun SaleReminderItem(row: SaleReminder, now: Instant, delete: () -> Unit, modifier: Modifier) {
    val text = rememberAppText()
    val state = rememberSwipeToDismissBoxState()
    val onDelete by rememberUpdatedState(delete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        modifier = modifier.testTag("sale-reminder:${row.id}"),
        backgroundContent = {
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, text(R.string.sale_reminder_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        SaleTimeCard(SaleTime(row.station, row.time), row.bureau, now,
            row.instant.atZone(saleZone).toLocalDate())
    }
}
