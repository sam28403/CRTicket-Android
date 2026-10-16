package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDateTimeFields(draft: HistoryDraft, enabled: Boolean) {
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { dateOpen = true }, enabled = enabled,
            modifier = Modifier.weight(1f).testTag("history-field-date"),
        ) { Text(draft.value("date").ifBlank { "开车日期" }) }
        OutlinedButton(
            onClick = { timeOpen = true }, enabled = enabled,
            modifier = Modifier.weight(1f).testTag("history-field-time"),
        ) { Text(draft.value("time").ifBlank { "开车时间" }) }
    }
    if (dateOpen) {
        val initial = runCatching {
            val parts = Regex("\\d+").findAll(draft.value("date")).map { it.value.toInt() }.toList()
            LocalDate.of(parts[0], parts[1], parts[2])
        }.getOrDefault(LocalDate.now())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        draft.fields["date"] = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC)
                            .toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                    }
                    dateOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("取消") } },
        ) { DatePicker(state, title = null) }
    }
    if (timeOpen) {
        val parts = draft.value("time").split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
            initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text("开车时间") },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    draft.fields["time"] = "%02d:%02d".format(java.util.Locale.ROOT, state.hour, state.minute)
                    timeOpen = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("取消") } },
        )
    }
}
