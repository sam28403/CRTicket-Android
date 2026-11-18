package cc.samlab.ticket.ui.trainquery

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
internal fun TrainQueryDatePicker(
    date: String,
    offline: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val min = today.minusDays(2)
    val max = today.plusDays(14)
    val entered = runCatching { LocalDate.parse(date) }.getOrDefault(today)
    val selected = if (offline) entered else entered.coerceIn(min, max)
    val selectable = remember(today, offline) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return offline || day in min..max
            }
            override fun isSelectableYear(year: Int) = offline || year in min.year..max.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        yearRange = if (offline) 1..9999 else min.year..max.year,
        selectableDates = selectable,
    )
    ExpressiveDatePickerDialog(
        state = state,
        wheelRange = if (offline) null else min..max,
        onDismiss = onDismiss,
        onSelect = { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
            onDismiss()
        },
    )
}
