package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.AppSwitch
import cc.samlab.ticket.i18n.rememberAppText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun monitorTime(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai"))
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("—")

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MonitorControls(controller: NativeTicketMonitorController) {
    val textResources = rememberAppText()
    val state = controller.state
    val ready = state.has("restoring") && !state.optBoolean("restoring")
    var calendar by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonitorStationField(controller, "from", textResources(R.string.ticket_from), ready, Modifier.weight(1f))
                FilledTonalIconButton(
                    { controller.action("swap") },
                    enabled = ready,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("monitor-swap")
                        .semantics { contentDescription = textResources(R.string.station_swap_description) },
                ) { Text("⇄", style = MaterialTheme.typography.headlineSmall) }
                MonitorStationField(controller, "to", textResources(R.string.ticket_to), ready, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { keyboard?.hide(); calendar = true },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("monitor-date"),
            ) { Text(textResources(R.string.monitor_departure_date, state.optString("date"))) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { keyboard?.hide(); controller.action("query") },
                    enabled = ready && !controller.loading && !controller.monitoring,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("monitor-query"),
                ) { Text(textResources(R.string.monitor_query)) }
                Text(textResources(R.string.monitor_toggle), style = MaterialTheme.typography.titleMedium)
                AppSwitch(
                    checked = controller.monitoring,
                    onCheckedChange = { controller.action(if (it) "start" else "stop") },
                    enabled = ready && (!controller.loading || controller.monitoring),
                    modifier = Modifier.testTag("monitor-toggle")
                        .semantics { contentDescription = textResources(R.string.monitor_toggle) },
                )
                FilledTonalIconButton(
                    onClick = { keyboard?.hide(); controller.action("reset") },
                    enabled = ready,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp).testTag("monitor-reset"),
                ) { Icon(Icons.Default.Refresh, textResources(R.string.monitor_reset)) }
            }
            Text(
                textResources(R.string.monitor_last_success, monitorTime(controller.lastMonitorAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("monitor-last-success"),
            )
        }
    }
    if (calendar) MonitorDatePicker(
        state.optString("date"), { calendar = false }, { controller.edit("date", it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorStationField(
    controller: NativeTicketMonitorController,
    key: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val show = expanded && controller.suggestionKey == key && controller.suggestions.isNotEmpty()
    ExposedDropdownMenuBox(show, { expanded = it }, modifier) {
        OutlinedTextField(
            value = controller.state.optString(key),
            onValueChange = {
                controller.edit(key, it)
                controller.suggest(key, it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().testTag("monitor-$key")
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(show, { expanded = false }) {
            controller.suggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { controller.edit(key, name); expanded = false },
                )
            }
        }
    }
}
