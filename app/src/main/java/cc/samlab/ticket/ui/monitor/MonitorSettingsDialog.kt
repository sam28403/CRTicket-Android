package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.railway.TrainNumberRules
import kotlin.math.roundToInt
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonitorSettingsDialog(
    controller: NativeTicketMonitorController,
    key: String,
    onDismiss: () -> Unit,
) {
    val textResources = rememberAppText()
    val haptics = LocalHapticFeedback.current
    val state = controller.state
    val title = when (key) {
        "interval" -> textResources(R.string.monitor_interval)
        "selectedDepartureStations" -> textResources(R.string.monitor_departure_filter)
        "selectedArrivalStations" -> textResources(R.string.monitor_arrival_filter)
        "timeRange" -> textResources(R.string.monitor_departure_time_filter)
        "trainFilters" -> textResources(R.string.monitor_train_search)
        "seatFilters" -> textResources(R.string.monitor_seat_filter)
        else -> textResources(R.string.monitor_train_type_filter)
    }
    var text by remember(key) { mutableStateOf(
        if (key == "interval") state.optInt("interval", 60).toString()
        else state.optJSONArray("trainFilters").strings().joinToString(" "),
    ) }
    var selected by remember(key) { mutableStateOf(state.optJSONArray(key).strings()) }
    var range by remember(key) { mutableStateOf(
        (state.optJSONArray("timeRange")?.optInt(0) ?: 0).toFloat()..
            (state.optJSONArray("timeRange")?.optInt(1) ?: 24).toFloat(),
    ) }
    var type by remember { mutableStateOf(when {
        state.optBoolean("highSpeedOnly") -> 1
        state.optBoolean("ordinaryOnly") -> 2
        else -> 0
    }) }
    val trains = text.uppercase().split(Regex("[\\s,，、]+"))
        .filter { it.isNotEmpty() }.distinct()
    val trainError = if (key == "trainFilters") {
        trains.firstNotNullOfOrNull { TrainNumberRules.error(it).takeIf(String::isNotEmpty) }
    } else null
    val valid = (key != "interval" || text.toIntOrNull() in 10..3600) && trainError == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (key) {
                        "interval", "trainFilters" -> OutlinedTextField(
                            text, { text = it },
                            modifier = Modifier.testTag("monitor-setting-input"),
                            textStyle = if (key == "interval") MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp)
                                else MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                            label = { Text(
                                if (key == "interval") textResources(R.string.monitor_interval_range) else textResources(R.string.monitor_train_search_hint),
                                fontSize = 16.sp,
                            ) },
                            supportingText = {
                                Text(
                                    trainError ?: if (key == "interval") textResources(R.string.monitor_interval_hint)
                                        else textResources(R.string.monitor_train_filter_hint),
                                    fontSize = 14.sp,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType =
                                if (key == "interval") KeyboardType.Number else KeyboardType.Text),
                            isError = !valid,
                        )
                        "timeRange" -> {
                            Text("%02d:00–%02d:00".format(range.start.toInt(), range.endInclusive.toInt()))
                            RangeSlider(
                                range, {
                                    val stepped = it.start.roundToInt().toFloat()..it.endInclusive.roundToInt().toFloat()
                                    if (stepped != range) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        range = stepped
                                    }
                                }, valueRange = 0f..24f, steps = 23,
                                modifier = Modifier.testTag("monitor-time-slider"),
                            )
                        }
                        "trainType" -> listOf(
                            textResources(R.string.monitor_train_all),
                            textResources(R.string.monitor_train_high_speed),
                            textResources(R.string.monitor_train_ordinary),
                        ).forEachIndexed { index, label ->
                            Row(
                                Modifier.fillMaxWidth().clickable { type = index }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(type == index, { type = index })
                                Text(label)
                            }
                        }
                        else -> {
                            val columns = state.optJSONArray("columns")
                            val options = if (key == "seatFilters") {
                                (0 until (columns?.length() ?: 0)).map {
                                    columns!!.getJSONArray(it).let { column -> column.getString(0) to column.getString(1) }
                                }
                            } else state.optJSONArray(
                                if (key == "selectedDepartureStations") "departureStations" else "arrivalStations",
                            ).strings().map { it to it }
                            TextButton({ selected = emptyList() }) { Text(textResources(R.string.filter_all)) }
                            if (options.isEmpty()) Text(textResources(R.string.monitor_station_filter_hint))
                            options.forEach { (value, label) ->
                                val toggle = { selected = if (value in selected) selected - value else selected + value }
                                Row(
                                    Modifier.fillMaxWidth().clickable { toggle() },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(value in selected, { toggle() })
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                when (key) {
                    "interval" -> controller.edit(key, text.toInt())
                    "trainFilters" -> controller.edit(key, JSONArray(trains))
                    "timeRange" -> controller.edit(key, JSONArray(listOf(range.start.toInt(), range.endInclusive.toInt())))
                    "trainType" -> {
                        controller.edit("highSpeedOnly", type == 1)
                        controller.edit("ordinaryOnly", type == 2)
                    }
                    else -> controller.edit(key, JSONArray(selected))
                }
                onDismiss()
            }, enabled = valid) { Text(textResources(R.string.action_confirm), fontSize = 16.sp) }
        },
        dismissButton = { TextButton(onDismiss) { Text(textResources(R.string.action_cancel), fontSize = 16.sp) } },
    )
}
