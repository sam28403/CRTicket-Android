package cc.samlab.ticket.ui.speedometer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import cc.samlab.ticket.ui.maps.MapSurface
import cc.samlab.ticket.R
import cc.samlab.ticket.speedometer.SpeedRecording
import cc.samlab.ticket.speedometer.recordingTime
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.TrainListRow
import org.json.JSONArray

@Composable
internal fun SpeedometerTrack(
    record: SpeedRecording?,
    onChooseSource: () -> Unit = {},
) {
    if (record == null) {
        Text(stringResource(R.string.speed_history_empty), Modifier.padding(24.dp))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            TrainListItem(
                row = TrainListRow(
                    record.train, record.from, record.to, recordingTime(record.elapsed), "",
                    decimal(record.meters / 1000), decimal(record.average),
                    0, record.meters / 1000, record.average, null,
                ),
                vehicle = record.vehicle.takeIf { it.isNotBlank() },
                showStationCount = false,
                enabled = false,
                onClick = {},
            )
        }
        if (record.points.isEmpty()) {
            Text(stringResource(R.string.speed_no_points), Modifier.padding(20.dp))
        } else {
            val segments = remember(record) {
                JSONArray(record.points.groupBy { it.segment }.values.map { points ->
                    JSONArray(points.map { JSONArray(listOf(it.latitude, it.longitude, it.time)) })
                }).toString()
            }
            MapSurface(
                segments = segments,
                dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                onChooseSource = onChooseSource,
            )
        }
    }
}
