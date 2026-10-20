package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun HistoryRecordCard(row: HistoryRecord, action: (String, String) -> Unit) {
    val textResources = rememberAppText()
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("history-record-${row.id}"),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.number,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, textResources(R.string.history_record_actions))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        listOf(
                            "download" to textResources(R.string.history_download),
                            "edit" to textResources(R.string.history_edit),
                            "delete" to textResources(R.string.history_delete),
                        )
                            .forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (key == "delete") colors.error else colors.onSurface) },
                                    onClick = {
                                        menu = false
                                        action(key, row.id)
                                    },
                                )
                            }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    row.from, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(10.dp), color = colors.secondaryContainer) {
                        Text(
                            row.train, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, null,
                        Modifier.padding(top = 4.dp).size(20.dp), tint = colors.primary,
                    )
                }
                Text(
                    row.to, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Text(
                listOf(row.date, row.time).filter { it.isNotBlank() }.joinToString(" · "),
                Modifier.padding(top = 16.dp, end = 8.dp).testTag("history-date"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    textResources(
                        if (row.credit) R.string.history_credit_amount else R.string.history_price_amount,
                        historyAmount(row.price),
                    ),
                    modifier = Modifier.testTag("history-price"),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary, fontWeight = FontWeight.Bold,
                )
                Text(
                    listOf(row.seatNo, row.seatType).filter { it.isNotBlank() }.joinToString(" · "),
                    Modifier.weight(1f).testTag("history-seat"),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
