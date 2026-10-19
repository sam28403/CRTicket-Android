package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.projectTitleFont
import org.json.JSONObject

@Composable
internal fun StationDiscoveryCard(
    row: JSONObject,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (rank != null) StationRankIcon(rank)
                Text(
                    row.optString("station"), Modifier.weight(1f),
                    color = colors.primary, style = MaterialTheme.typography.headlineSmall,
                    fontFamily = projectTitleFont, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Surface(shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer) {
                    Text(
                        "共 ${row.optInt("total")} 趟",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("始发 ${row.optInt("origin")} 趟", style = MaterialTheme.typography.titleMedium)
                Text("终到 ${row.optInt("terminal")} 趟", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "已接发：${row.optInt("handled")} / ${row.optInt("total")}，" +
                    "在站过路车：${row.optInt("passing")}",
                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
