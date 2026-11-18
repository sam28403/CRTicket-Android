package cc.samlab.ticket.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DateWheelFields(
    state: DatePickerState,
    limit: ClosedRange<LocalDate>?,
    onScrollingChange: (Boolean) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val min = limit?.start ?: LocalDate.of(state.yearRange.first, 1, 1)
    val max = limit?.endInclusive ?: LocalDate.of(state.yearRange.last, 12, 31)
    val initial = remember(min, max) {
        Instant.ofEpochMilli(state.selectedDateMillis ?: state.displayedMonthMillis)
            .atZone(ZoneOffset.UTC).toLocalDate().coerceIn(min, max)
    }
    val yearRange = min.year..max.year
    val years = rememberDateWheel(yearRange, initial.year)
    val year = yearRange.first + years.rememberSettledOption()

    var lastMonth by rememberSaveable { mutableIntStateOf(initial.monthValue) }
    val monthRange = (if (year == min.year) min.monthValue else 1)..
        (if (year == max.year) max.monthValue else 12)
    val months = rememberDateWheel(monthRange, lastMonth)
    val month = monthRange.first + months.rememberSettledOption()

    var lastDay by rememberSaveable { mutableIntStateOf(initial.dayOfMonth) }
    val monthStart = LocalDate.of(year, month, 1)
    val dayRange = (if (monthStart == min.withDayOfMonth(1)) min.dayOfMonth else 1)..
        (if (monthStart == max.withDayOfMonth(1)) max.dayOfMonth else monthStart.lengthOfMonth())
    val days = rememberDateWheel(dayRange, lastDay)
    LaunchedEffect(year, month, days) {
        snapshotFlow {
            val scrolling = years.isScrollInProgress || months.isScrollInProgress || days.isScrollInProgress
            val canSelect = !scrolling && year == yearRange.first + years.selectedOption &&
                month == monthRange.first + months.selectedOption
            if (canSelect) dayRange.first + days.selectedOption else null
        }.collect { day ->
            if (day != null) {
                lastMonth = month
                lastDay = day
                onSelect(monthStart.withDayOfMonth(day))
            }
            onScrollingChange(day == null)
        }
    }

    val text = rememberAppText()
    val locale = state.locale
    val order = remember(locale) {
        DateFormat.getBestDateTimePattern(locale, "yMd").filter { it in "yMd" }.toSet()
    }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (field in order) {
            key(field) {
                when (field) {
                    'y' -> DateWheel(
                        years, yearRange.first, text(R.string.date_picker_year), numbers,
                        tag = "date-wheel-year", modifier = Modifier.weight(1.2f),
                    )
                    'M' -> DateWheel(
                        months, monthRange.first, text(R.string.date_picker_month), numbers,
                        tag = "date-wheel-month", modifier = Modifier.weight(1f),
                    )
                    'd' -> DateWheel(
                        days, dayRange.first, text(R.string.date_picker_day), numbers,
                        tag = "date-wheel-day", modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberDateWheel(range: IntRange, selected: Int): PickerWheelState = key(range) {
    rememberPickerWheelState(range.count(), selected.coerceIn(range) - range.first)
}

@Composable
private fun PickerWheelState.rememberSettledOption(): Int {
    var option by remember(this) { mutableIntStateOf(selectedOption) }
    LaunchedEffect(this) {
        snapshotFlow { if (isScrollInProgress) null else selectedOption }
            .filterNotNull()
            .collect { option = it }
    }
    return option
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DateWheel(
    state: PickerWheelState,
    first: Int,
    description: String,
    numbers: NumberFormat,
    tag: String,
    modifier: Modifier,
) {
    val colors = ScrollFieldDefaults.colors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PickerWheel(
            state = state,
            description = description,
            modifier = Modifier.fillMaxWidth().height(160.dp).testTag(tag),
            colors = colors,
            optionDescription = { numbers.format(first + it) },
        ) { index, selected, enabled ->
            if (enabled || selected) {
                Text(
                    text = remember(numbers, first, index) { numbers.format(first + index) },
                    style = if (selected) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                    fontFamily = projectTitleFont,
                    maxLines = 1,
                    color = if (selected) colors.selectedContentColor else colors.contentColor,
                )
            }
        }
    }
}
