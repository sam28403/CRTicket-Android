package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.projectTitleFont

@Composable
internal fun StationBoardItem(
    row: StationBoardRow,
    tab: StationTab,
    highlight: StationHighlight,
    dark: Boolean,
    onTrain: () -> Unit,
    onStation: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (highlight) {
        StationHighlight.IN_STATION -> if (dark) Color(0xFFFFC078) else Color(0xFF975009)
        StationHighlight.UPCOMING -> if (dark) Color(0xFF70DF97) else Color(0xFF18763B)
        StationHighlight.NONE -> colors.primary
    }
    Surface(
        onClick = onTrain,
        color = if (highlight == StationHighlight.NONE) colors.surfaceContainerLow
            else accent.copy(alpha = if (dark) 0.14f else 0.09f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("station-card-${row.id}"),
    ) {
        if (tab == StationTab.ALL) Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StationTrainCode(
                    row.train,
                    accent,
                    onTrain,
                    fontFamily = projectTitleFont,
                )
                StationStatus(row, highlight, accent)
            }
            StationRoute(row.from, row.to, onStation)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StationTimeField("到达", row.arrival, Modifier.weight(1f), accent)
                StationTimeField("开车", row.departure, Modifier.weight(1f), accent)
                StationTimeField("停留", row.stay, Modifier.weight(1f))
            }
        } else Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                StationTrainCode(row.train, accent, onTrain)
                StationStatus(row, highlight, accent)
            }
            Column(Modifier.weight(1.3f)) {
                Text(
                    if (tab == StationTab.DEPARTURE) "开往" else "来自",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                val station = if (tab == StationTab.DEPARTURE) row.to else row.from
                StationName(station, onStation = { onStation(station) })
            }
            StationTimeField(
                if (tab == StationTab.DEPARTURE) "开车" else "到达",
                if (tab == StationTab.DEPARTURE) row.departure else row.arrival,
                Modifier.widthIn(min = 64.dp), accent,
                prominent = true,
            )
        }
    }
}
