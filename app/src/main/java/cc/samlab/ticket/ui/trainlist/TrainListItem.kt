package cc.samlab.ticket.ui.trainlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.railfan.VehicleNumberBadge

@Composable
internal fun TrainListItem(
    row: TrainListRow,
    vehicle: String? = null,
    onVehicleClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    showStationCount: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth()
            .testTag("train-card:${row.train}")
            .semantics { selected = highlighted },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    row.train,
                    Modifier.weight(1f),
                    color = if (highlighted) colors.onPrimaryContainer else colors.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = projectTitleFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (vehicle != null) {
                    VehicleNumberBadge(vehicle, onVehicleClick ?: onClick)
                }
                if (showStationCount) Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.secondaryContainer,
                ) {
                    Text(
                        stringResource(R.string.train_list_station_count, row.stations),
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
            TrainListDetails(row, highlighted)
        }
    }
}

@Composable
private fun TrainListDetails(row: TrainListRow, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val detailColor = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            row.from,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = detailColor)
        Text(
            row.to,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        )
    }
    Text(
        stringResource(R.string.train_list_metrics, row.duration, row.mileage, row.speed),
        Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = detailColor,
        maxLines = 2,
    )
}
