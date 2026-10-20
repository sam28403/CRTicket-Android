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
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import org.json.JSONObject

@Composable
internal fun StationDiscoveryCard(
    row: JSONObject,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    val textResources = rememberAppText()
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
                        textResources(R.string.train_list_total, row.optInt("total")),
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
                Text(
                    textResources(R.string.station_discovery_origin, row.optInt("origin")),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    textResources(R.string.station_discovery_terminal, row.optInt("terminal")),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                textResources(
                    R.string.station_discovery_summary,
                    row.optInt("handled"),
                    row.optInt("total"),
                    row.optInt("passing"),
                ),
                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
