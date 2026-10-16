package cc.samlab.ticket.ui.station

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun StationTrainCode(code: String, color: Color, onClick: () -> Unit) {
    Text(
        code,
        Modifier.heightIn(min = 36.dp).clickable(onClick = onClick).wrapContentHeight()
            .testTag("station-train-code"),
        color = color, style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
internal fun StationName(name: String, modifier: Modifier = Modifier, onStation: () -> Unit) {
    Text(
        name,
        modifier.heightIn(min = 48.dp).clickable(onClick = onStation).wrapContentHeight()
            .testTag("station-name"),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun StationRoute(from: String, to: String, onStation: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationName(from, Modifier.weight(1f)) { onStation(from) }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward, null,
            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StationName(to, Modifier.weight(1f)) { onStation(to) }
    }
}

@Composable
internal fun StationTimeField(
    label: String,
    value: String,
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    prominent: Boolean = false,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (value == "始发站" || value == "终到站") "—" else value,
            style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

@Composable
internal fun StationStatus(row: StationBoardRow, highlight: StationHighlight, color: Color) {
    if (highlight != StationHighlight.NONE) Text(
        if (highlight == StationHighlight.IN_STATION) "停在站内" else row.highlightLabel,
        color = color, style = MaterialTheme.typography.labelMedium,
    )
}
