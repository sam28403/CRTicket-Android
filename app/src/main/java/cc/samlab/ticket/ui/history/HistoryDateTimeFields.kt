package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryDateTimeFields(draft: HistoryDraft, enabled: Boolean) {
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    val date = draft.value("date")
    val dateLabel = remember(date) {
        historyEditorDate(date)?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            ?: date.ifBlank { "开车日期" }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { dateOpen = true },
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag("history-field-date"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(dateLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = { timeOpen = true },
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag("history-field-time"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                draft.value("time").ifBlank { "开车时间" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (dateOpen) {
        HistoryDatePicker(
            date = date,
            onDismiss = { dateOpen = false },
            onSelect = {
                draft.fields["date"] = it
                dateOpen = false
            },
        )
    }
    if (timeOpen) {
        HistoryTimePicker(
            time = draft.value("time"),
            onDismiss = { timeOpen = false },
            onSelect = {
                draft.fields["time"] = it
                timeOpen = false
            },
        )
    }
}

internal fun historyEditorDate(value: String): LocalDate? = runCatching {
    val parts = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
    LocalDate.of(parts[0], parts[1], parts[2])
}.getOrNull()
