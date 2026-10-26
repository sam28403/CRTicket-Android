package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
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
    modifier: Modifier = Modifier,
    status: String? = null,
    tag: String? = null,
    compact: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val textResources = rememberAppText()
    val colors = MaterialTheme.colorScheme
    val handledRatio = row.optDouble("handledRatio", 0.0).toFloat().coerceIn(0f, 1f)
    val progressColor = if (handledRatio >= 1f) {
        Color(0xFF4CAF50).copy(alpha = 0.22f)
    } else colors.primary.copy(alpha = 0.12f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().testTag(
            tag ?: if (rank != null) "station-busy-card:$rank"
            else "station-random-card:${row.optString("station")}",
        ),
    ) {
        Column(Modifier.drawBehind {
            val width = size.width * handledRatio
            drawRect(
                color = progressColor,
                topLeft = Offset(
                    if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f,
                    0f,
                ),
                size = Size(width, size.height),
            )
        }.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (rank != null) StationRankIcon(rank)
                Text(
                    row.optString("station"), Modifier.weight(1f),
                    color = if (highlighted) colors.onPrimaryContainer else colors.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = projectTitleFont, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!compact) Surface(shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer) {
                    Text(
                        if (status == null) textResources(R.string.train_list_total, row.optInt("total"))
                        else "—",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
            if (!compact && status != null) {
                Text(
                    status,
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else if (!compact) {
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
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        textResources(
                            R.string.station_discovery_summary,
                            row.optInt("handled"),
                            row.optInt("total"),
                            row.optInt("passing"),
                        ),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    val bureau = row.optString("bureau")
                    if (bureau.isNotBlank()) {
                        Text(
                            bureau,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
