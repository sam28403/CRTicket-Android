package cc.samlab.ticket.ui.speedometer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.speedometer.SpeedRecording
import cc.samlab.ticket.speedometer.recordingTime
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.TrainListRow

@Composable
internal fun SpeedometerHistoryItem(
    record: SpeedRecording,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState()
    val delete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) {
            delete()
            dismiss.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier.testTag("speed-record:${record.id}"),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        TrainListItem(
            row = TrainListRow(
                record.train, record.from, record.to,
                recordingTime(record.elapsed), "", decimal(record.meters / 1000), decimal(record.average),
                0, record.meters / 1000, record.average, null,
            ),
            vehicle = record.vehicle.takeIf { it.isNotBlank() },
            showStationCount = false,
            onClick = onClick,
        )
    }
}
