package cc.samlab.ticket.ui.speedometer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.speedometer.*

@Composable
internal fun SpeedometerLive(onBack: () -> Unit) {
    val context = LocalContext.current
    val record = SpeedometerStore.current
    var stop by remember { mutableStateOf(false) }
    if (record == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (SpeedometerStore.error) R.string.speed_start_error else R.string.speed_finished))
            Button(onClick = onBack) { Text(stringResource(R.string.about_back)) }
        }
        return
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("speed-live-content"),
    ) {
        val compact = maxHeight < 420.dp
        val gap = if (compact) 6.dp else 12.dp
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpeedGauge(SpeedometerStore.speed, speedometerMaximum(record.train), Modifier.weight(1f))
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    SpeedometerSignal(compact)
                    SpeedometerJourney(record, compact)
                    Spacer(Modifier.weight(1f))
                    SpeedometerMetrics(record, compact)
                    SpeedometerControls(compact, { stop = true })
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                SpeedometerSignal(compact)
                SpeedGauge(SpeedometerStore.speed, speedometerMaximum(record.train), Modifier.weight(1f))
                SpeedometerJourney(record, compact)
                SpeedometerMetrics(record, compact)
                SpeedometerControls(compact, { stop = true })
            }
        }
    }
    if (stop) AlertDialog(
        onDismissRequest = { stop = false },
        title = { Text(stringResource(R.string.speed_stop_title)) },
        text = { Text(stringResource(R.string.speed_stop_body)) },
        confirmButton = { TextButton(onClick = {
            SpeedometerService.command(context, SpeedometerService.STOP)
            stop = false
            onBack()
        }) { Text(stringResource(R.string.speed_stop)) } },
        dismissButton = { TextButton(onClick = { stop = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}
