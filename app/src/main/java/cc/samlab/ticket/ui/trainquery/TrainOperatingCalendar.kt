package cc.samlab.ticket.ui.trainquery

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

internal data class OperatingCalendarState(
    val days: TrainOperatingDays? = null,
    val loading: Boolean = true,
)

@Composable
internal fun rememberOperatingCalendar(
    train: String,
    date: String,
): State<OperatingCalendarState> = key(train, date) {
    produceState(OperatingCalendarState(), train, date) {
        if (train.isEmpty()) {
            value = OperatingCalendarState(loading = false)
            return@produceState
        }
        value = try {
            OperatingCalendarState(
                days = TrainOperatingDaysRepository.query(train.substringBefore('/'), LocalDate.parse(date)),
                loading = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            OperatingCalendarState(loading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainOperatingCalendar(
    state: OperatingCalendarState,
    date: String,
    dark: Boolean,
) {
    val days = state.days?.takeIf { it.dates.isNotEmpty() } ?: return
    val text = rememberAppText()
    val colors = MaterialTheme.colorScheme
    val selected = remember(date) {
        runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now(ZoneId.of("Asia/Shanghai")))
    }
    val months = days.months
    var month by remember(date, state.days) {
        mutableStateOf(YearMonth.from(selected).let { requested ->
            if (months.isEmpty() || requested in months) requested else months.first()
        })
    }
    val previous = months.lastOrNull { it < month }
    Surface(
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
            .testTag("train-operating-calendar"),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLowest,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val next = months.firstOrNull {
                it > month && (!wide || it < months.last())
            }
            Column(
                Modifier.animateContentSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text(R.string.train_calendar_title), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    FilledTonalIconButton(
                        onClick = { previous?.let { month = it } },
                        enabled = previous != null,
                        modifier = Modifier.testTag("train-calendar-previous"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, text(R.string.train_calendar_previous))
                    }
                    FilledTonalIconButton(
                        onClick = { next?.let { month = it } },
                        enabled = next != null,
                        modifier = Modifier.testTag("train-calendar-next"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, text(R.string.train_calendar_next))
                    }
                }
                TrainOperatingCalendarLegend(dark)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    OperatingCalendarMonth(month, selected, days, dark, Modifier.weight(1f))
                    if (wide) OperatingCalendarMonth(
                        month.plusMonths(1), selected, days, dark, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OperatingCalendarMonth(
    month: YearMonth,
    selected: LocalDate,
    days: TrainOperatingDays,
    dark: Boolean,
    modifier: Modifier,
) {
    val text = rememberAppText()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text(R.string.train_calendar_month, month.year, month.monthValue),
            Modifier.fillMaxWidth().testTag("train-calendar-month-$month"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        TrainOperatingCalendarGrid(month, selected, days, dark)
    }
}
