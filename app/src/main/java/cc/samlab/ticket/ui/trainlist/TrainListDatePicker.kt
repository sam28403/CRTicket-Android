package cc.samlab.ticket.ui.trainlist

import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import cc.samlab.ticket.ui.components.ExpressiveDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun TrainListDatePicker(date: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val selected = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = selected)
    ExpressiveDatePickerDialog(
        state = state,
        onDismiss = onDismiss,
        onSelect = { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
            onDismiss()
        },
    )
}
