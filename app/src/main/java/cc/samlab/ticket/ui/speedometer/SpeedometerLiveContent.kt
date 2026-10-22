package cc.samlab.ticket.ui.speedometer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.speedometer.*
import cc.samlab.ticket.ui.home.projectTitleFont

@Composable
internal fun SpeedometerSignal(compact: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(when {
                SpeedometerStore.paused -> R.string.speed_paused
                SpeedometerStore.signal -> R.string.speed_signal_good
                SpeedometerStore.current?.points?.isEmpty() == true -> R.string.speed_waiting
                else -> R.string.speed_signal_lost
            }),
            Modifier.padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SpeedometerJourney(record: SpeedRecording, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            record.train,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            fontFamily = projectTitleFont,
            maxLines = 1,
        )
        Text(
            "${record.from} → ${record.to}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SpeedometerMetrics(record: SpeedRecording, compact: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp),
        ) {
            SpeedometerMetric(R.string.speed_elapsed, recordingTime(record.elapsed), compact)
            SpeedometerMetric(R.string.speed_distance, stringResource(R.string.speed_km, decimal(record.meters / 1000)), compact)
            SpeedometerMetric(R.string.speed_average, stringResource(R.string.speed_kmh, decimal(record.average)), compact)
        }
    }
}

@Composable
private fun SpeedometerMetric(label: Int, value: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = style)
        Text(value, style = style)
    }
}

@Composable
internal fun SpeedometerControls(compact: Boolean, onStop: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = { SpeedometerService.command(context, SpeedometerService.PAUSE) },
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(if (SpeedometerStore.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (SpeedometerStore.paused) R.string.speed_resume else R.string.speed_pause), maxLines = 1)
        }
        Button(
            onClick = onStop,
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.Stop, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.speed_stop), maxLines = 1)
        }
    }
}
