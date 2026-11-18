package cc.samlab.ticket.ui.history

import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import cc.samlab.ticket.ui.components.ExpressiveDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryDatePicker(
    date: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val initial = historyEditorDate(date) ?: LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    ExpressiveDatePickerDialog(
        state = state,
        onDismiss = onDismiss,
        onSelect = { millis ->
            // Keep the ticket format expected by validation, persistence and export.
            onSelect(
                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
            )
        },
    )
}
