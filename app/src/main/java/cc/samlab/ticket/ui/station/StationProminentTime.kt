package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun StationProminentTime(label: String, value: String, color: Color) {
    val annotation = Regex("\\s+(\\([+-]\\d+'\\))$").find(value)
    val clock = if (annotation == null) value else value.substring(0, annotation.range.first)
    Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (annotation != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    annotation.groupValues[1],
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
        Text(clock, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}
