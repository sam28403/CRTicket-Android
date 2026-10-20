package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun MonitorTicketCard(row: JSONObject, columns: JSONArray?) {
    val textResources = rememberAppText()
    val colors = MaterialTheme.colorScheme
    val seats = row.optJSONObject("seats") ?: JSONObject()
    val palette = monitorCardColors(seats, colors)
    val known = (0 until (columns?.length() ?: 0)).map { columns!!.getJSONArray(it) }
        .filter { seats.optString(it.getString(0)) !in listOf("", "--", "—", "*", "null") }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = palette.container,
        contentColor = palette.content,
        modifier = Modifier.fillMaxWidth().testTag("monitor-ticket-${row.optString("train")}"),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.optString("train"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = projectTitleFont,
                    color = colors.primary,
                )
                Text(row.optString("duration"), style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(row.optString("departure"), style = MaterialTheme.typography.titleLarge)
                    Text(row.optString("from"))
                }
                Text("→", Modifier.padding(top = 12.dp), color = palette.accent)
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.optString("arrival"), style = MaterialTheme.typography.titleLarge)
                    Text(row.optString("to"))
                }
            }
            HorizontalDivider(color = palette.divider)
            known.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { column ->
                        val value = seats.optString(column.getString(0))
                        val count = value.toIntOrNull()
                        val available = value in listOf("有", ">20") || (count ?: 0) > 0
                        Column(Modifier.weight(1f)) {
                            Text(column.getString(1), style = MaterialTheme.typography.labelMedium)
                            Text(
                                when {
                                    value in listOf("有", ">20") -> textResources(R.string.monitor_tickets_many)
                                    value == "无" -> textResources(R.string.monitor_tickets_zero)
                                    count != null -> textResources(R.string.monitor_tickets_count, count)
                                    else -> value
                                },
                                color = if (available) palette.accent else palette.muted,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (known.isEmpty()) Text(textResources(R.string.monitor_seats_empty), color = palette.muted)
            val status = row.optString("status").replace("预订", "").trim()
            if (!row.optBoolean("canBuy") && status.isNotEmpty()) Text(status, color = colors.error)
        }
    }
}
