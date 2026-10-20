package cc.samlab.ticket.ui.trainquery

import androidx.compose.material3.*
import androidx.compose.runtime.*
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainQueryDatePicker(date: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val textResources = rememberAppText()
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val min = today.minusDays(2)
    val max = today.plusDays(14)
    val selected = runCatching { LocalDate.parse(date) }.getOrDefault(today).coerceIn(min, max)
    val selectable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return day in min..max
            }
            override fun isSelectableYear(year: Int) = year in min.year..max.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }, enabled = state.selectedDateMillis != null) { Text(textResources(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textResources(R.string.action_cancel)) } },
    ) { DatePicker(state = state, title = null, showModeToggle = false) }
}
