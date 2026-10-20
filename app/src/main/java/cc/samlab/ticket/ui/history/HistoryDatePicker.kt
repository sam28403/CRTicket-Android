package cc.samlab.ticket.ui.history

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDatePicker(
    date: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val textResources = rememberAppText()
    val initial = historyEditorDate(date) ?: LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        // Keep the ticket format expected by validation, persistence and export.
                        onSelect(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                        )
                    }
                },
            ) { Text(textResources(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textResources(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
