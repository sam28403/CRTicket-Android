package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val state = controller.state
    val ready = state.has("restoring") && !state.optBoolean("restoring")
    var calendar by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonitorStationField(controller, "from", "出发站", ready, Modifier.weight(1f))
                FilledTonalIconButton(
                    { controller.action("swap") },
                    enabled = ready,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("monitor-swap")
                        .semantics { contentDescription = "交换出发站和到达站" },
                ) { Text("⇄", style = MaterialTheme.typography.headlineSmall) }
                MonitorStationField(controller, "to", "到达站", ready, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { keyboard?.hide(); calendar = true },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("monitor-date"),
            ) { Text("出发日期  ${state.optString("date")}") }
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
                ) { Text("查询余票") }
                Text("监控", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = controller.monitoring,
                    onCheckedChange = { controller.action(if (it) "start" else "stop") },
                    enabled = ready && (!controller.loading || controller.monitoring),
                    modifier = Modifier.testTag("monitor-toggle")
                        .semantics { contentDescription = "监控" },
                )
                FilledTonalIconButton(
                    onClick = { keyboard?.hide(); controller.action("reset") },
                    enabled = ready,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp).testTag("monitor-reset"),
                ) { Icon(Icons.Default.Refresh, "重置余票监控设定") }
            }
            Text(
                "上次监控成功：${monitorTime(controller.lastMonitorAt)}",
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
