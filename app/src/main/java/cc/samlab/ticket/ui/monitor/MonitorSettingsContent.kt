package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.ui.settings.SettingsGroup
import cc.samlab.ticket.ui.settings.SettingsRow
import cc.samlab.ticket.ui.settings.SettingsPanel

@Composable
internal fun MonitorSettingsContent(
    controller: NativeTicketMonitorController,
    embedded: Boolean = false,
) {
    val state = controller.state
    var dialog by remember { mutableStateOf("") }
    val enabled = state.has("restoring") && !state.optBoolean("restoring")
    val selected: (String) -> String = { state.optJSONArray(it).strings().joinToString("、").ifEmpty { "全部" } }
    SettingsPanel(rememberScrollState(), "monitor-settings-content", embedded) {
        SettingsGroup("监控", first = true) {
            SettingsRow(
                "监控间隔", "${state.optInt("interval", 60)} 秒", 0, 1,
                enabled && !controller.monitoring, { dialog = "interval" },
            )
        }
        SettingsGroup("车站与时间") {
            SettingsRow("出发车站筛选", selected("selectedDepartureStations"), 0, 3, enabled,
                { dialog = "selectedDepartureStations" })
            SettingsRow("到达车站筛选", selected("selectedArrivalStations"), 1, 3, enabled,
                { dialog = "selectedArrivalStations" })
            val range = state.optJSONArray("timeRange")
            val start = range?.optInt(0) ?: 0
            val end = range?.optInt(1) ?: 24
            SettingsRow("出发时间筛选", "%02d:00–%02d:00".format(start, end), 2, 3, enabled,
                { dialog = "timeRange" })
        }
        SettingsGroup("车次与席别") {
            SettingsRow("车次搜索", selected("trainFilters"), 0, 4, enabled,
                { dialog = "trainFilters" })
            val columns = state.optJSONArray("columns")
            val seats = state.optJSONArray("seatFilters").strings()
            val labels = (0 until (columns?.length() ?: 0)).map { columns!!.getJSONArray(it) }
                .filter { it.getString(0) in seats }.joinToString("、") { it.getString(1) }
            SettingsRow("席别选定", labels.ifEmpty { "全部席别" }, 1, 4, enabled,
                { dialog = "seatFilters" })
            SettingsRow("车次类型选定", when {
                state.optBoolean("highSpeedOnly") -> "高速动车"
                state.optBoolean("ordinaryOnly") -> "普速列车"
                else -> "全部车次"
            }, 2, 4, enabled, { dialog = "trainType" })
            SettingsRow("仅看有票", "只显示关注席别有票的车次", 3, 4, enabled,
                { controller.edit("onlyAvailable", !state.optBoolean("onlyAvailable")) }) {
                Switch(
                    state.optBoolean("onlyAvailable"), { controller.edit("onlyAvailable", it) },
                    enabled = enabled, modifier = Modifier.testTag("monitor-only-available"),
                )
            }
        }
    }
    if (dialog.isNotEmpty()) MonitorSettingsDialog(controller, dialog) { dialog = "" }
}
