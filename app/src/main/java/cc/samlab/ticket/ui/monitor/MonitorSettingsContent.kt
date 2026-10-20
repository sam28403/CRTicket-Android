package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.settings.SettingsGroup
import cc.samlab.ticket.ui.settings.SettingsPanel
import cc.samlab.ticket.ui.settings.SettingsRow

@Composable
internal fun MonitorSettingsContent(
    controller: NativeTicketMonitorController,
    embedded: Boolean = false,
) {
    val textResources = rememberAppText()
    val state = controller.state
    var dialog by remember { mutableStateOf("") }
    val enabled = state.has("restoring") && !state.optBoolean("restoring")
    val selected: (String) -> String = { key ->
        state.optJSONArray(key).strings().joinToString("、")
            .ifEmpty { textResources(R.string.filter_all) }
    }
    SettingsPanel(rememberScrollState(), "monitor-settings-content", embedded) {
        SettingsGroup(textResources(R.string.monitor_toggle), first = true) {
            SettingsRow(
                textResources(R.string.monitor_interval),
                textResources(R.string.duration_seconds, state.optInt("interval", 60)),
                0, 1,
                enabled && !controller.monitoring, { dialog = "interval" },
            )
        }
        SettingsGroup(textResources(R.string.monitor_station_time_group)) {
            SettingsRow(
                textResources(R.string.monitor_departure_filter),
                selected("selectedDepartureStations"),
                0, 3, enabled,
                { dialog = "selectedDepartureStations" },
            )
            SettingsRow(
                textResources(R.string.monitor_arrival_filter),
                selected("selectedArrivalStations"),
                1, 3, enabled,
                { dialog = "selectedArrivalStations" },
            )
            val range = state.optJSONArray("timeRange")
            val start = range?.optInt(0) ?: 0
            val end = range?.optInt(1) ?: 24
            SettingsRow(
                textResources(R.string.monitor_departure_time_filter),
                "%02d:00–%02d:00".format(start, end),
                2, 3, enabled,
                { dialog = "timeRange" },
            )
        }
        SettingsGroup(textResources(R.string.monitor_train_seat_group)) {
            SettingsRow(
                textResources(R.string.monitor_train_search), selected("trainFilters"),
                0, 4, enabled,
                { dialog = "trainFilters" },
            )
            val columns = state.optJSONArray("columns")
            val seats = state.optJSONArray("seatFilters").strings()
            val labels = (0 until (columns?.length() ?: 0)).map { columns!!.getJSONArray(it) }
                .filter { it.getString(0) in seats }.joinToString("、") { it.getString(1) }
            SettingsRow(
                textResources(R.string.monitor_seat_filter),
                labels.ifEmpty { textResources(R.string.monitor_seat_all) },
                1, 4, enabled,
                { dialog = "seatFilters" },
            )
            SettingsRow(
                textResources(R.string.monitor_train_type_filter),
                when {
                    state.optBoolean("highSpeedOnly") -> textResources(R.string.monitor_train_high_speed)
                    state.optBoolean("ordinaryOnly") -> textResources(R.string.monitor_train_ordinary)
                    else -> textResources(R.string.monitor_train_all)
                },
                2, 4, enabled, { dialog = "trainType" },
            )
            SettingsRow(
                textResources(R.string.monitor_available_only),
                textResources(R.string.monitor_available_only_description),
                3, 4, enabled,
                { controller.edit("onlyAvailable", !state.optBoolean("onlyAvailable")) },
            ) {
                Switch(
                    state.optBoolean("onlyAvailable"), { controller.edit("onlyAvailable", it) },
                    enabled = enabled, modifier = Modifier.testTag("monitor-only-available"),
                )
            }
        }
    }
    if (dialog.isNotEmpty()) MonitorSettingsDialog(controller, dialog) { dialog = "" }
}
