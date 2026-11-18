package cc.samlab.ticket.ui.monitor

import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cc.samlab.ticket.ui.components.ExpressiveDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
internal fun MonitorDatePicker(date: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val max = today.plusDays(14)
    val selected = runCatching { LocalDate.parse(date) }.getOrDefault(today).coerceIn(today, max)
    val selectable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return day in today..max
            }
            override fun isSelectableYear(year: Int) = year in today.year..max.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = selectable,
    )
    ExpressiveDatePickerDialog(
        state = state,
        wheelRange = today..max,
        onDismiss = onDismiss,
        onSelect = { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
            onDismiss()
        },
    )
}
