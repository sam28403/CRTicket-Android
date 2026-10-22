package cc.samlab.ticket.ui.speedometer

import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.R
import cc.samlab.ticket.railway.TrainNumberRules
import cc.samlab.ticket.speedometer.*
import cc.samlab.ticket.ui.history.HistoryStation
import java.util.Locale

internal fun decimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@Composable
internal fun SpeedometerSetup(
    stations: List<HistoryStation>,
    navigate: (String) -> Unit,
    active: Boolean,
) {
    var train by rememberSaveable { mutableStateOf("") }
    var vehicle by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var validate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val deleted = stringResource(R.string.speed_deleted)
    val undo = stringResource(R.string.speed_undo)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.speed_setup), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = train,
                            onValueChange = { train = it.uppercase(Locale.ROOT).trim().take(6) },
                            label = { Text(stringResource(R.string.speed_train)) },
                            isError = validate && !TrainNumberRules.isValid(train),
                            supportingText = if (validate && !TrainNumberRules.isValid(train)) {
                                { Text(stringResource(R.string.train_error_invalid)) }
                            } else null,
                            singleLine = true, shape = MaterialTheme.shapes.large,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("speed-train"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SpeedStationField(from, { from = it }, R.string.ticket_from, stations, Modifier.weight(1f))
                            SpeedStationField(to, { to = it }, R.string.ticket_to, stations, Modifier.weight(1f))
                        }
                        OutlinedTextField(
                            value = vehicle,
                            onValueChange = { vehicle = it.uppercase(Locale.ROOT).trim().take(40) },
                            label = { Text(stringResource(R.string.speed_vehicle_optional)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("speed-vehicle"),
                        )
                        if (validate && (from.isBlank() || to.isBlank())) {
                            Text(stringResource(R.string.speed_station_error), color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = {
                            validate = true
                            if (TrainNumberRules.isValid(train) && from.isNotBlank() && to.isNotBlank()) {
                                navigate(
                                    "$SPEED_ROUTE/guide?train=${Uri.encode(train)}" +
                                        "&from=${Uri.encode(from.trim())}&to=${Uri.encode(to.trim())}" +
                                        "&vehicle=${Uri.encode(vehicle)}",
                                )
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = SpeedometerStore.current == null) {
                            Text(stringResource(R.string.speed_start))
                        }
                        if (SpeedometerStore.current != null) FilledTonalButton(
                            onClick = { navigate("$SPEED_ROUTE/live") }, modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.speed_return)) }
                        if (SpeedometerStore.error) Text(stringResource(R.string.speed_start_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Text(stringResource(R.string.speed_history), style = MaterialTheme.typography.titleMedium) }
            if (SpeedometerStore.history.isEmpty()) item {
                ExpressiveEmptyState(
                    message = stringResource(R.string.speed_history_empty),
                    visible = active,
                    modifier = Modifier.fillMaxWidth().testTag("speed-history-empty"),
                    artworkModifier = Modifier.size(96.dp),
                )
            }
            items(SpeedometerStore.history, key = { it.id }) { record ->
                SpeedometerHistoryItem(
                    record = record,
                    modifier = Modifier.animateItem(),
                    onClick = { navigate("$SPEED_ROUTE/track?id=${record.id}") },
                    onDelete = {
                        val removed = SpeedometerStore.delete(record.id)
                        if (removed != null) {
                            undoJob?.cancel()
                            snackbar.currentSnackbarData?.dismiss()
                            undoJob = scope.launch {
                                if (snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) ==
                                    SnackbarResult.ActionPerformed) {
                                    SpeedometerStore.restore(removed)
                                }
                            }
                        }
                    },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
