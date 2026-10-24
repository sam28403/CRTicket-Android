package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
internal fun TrainOperatingCalendarLegend(dark: Boolean) {
    val text = rememberAppText()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text(R.string.train_calendar_running),
            color = if (dark) Color(0xFFB5F1BF) else Color(0xFF145C2E),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text(R.string.train_calendar_stopped),
            color = if (dark) Color(0xFFFFB4AB) else Color(0xFF932C26),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun TrainOperatingCalendarGrid(
    month: YearMonth,
    queryDate: LocalDate,
    days: TrainOperatingDays?,
    dark: Boolean,
) {
    val text = rememberAppText()
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val runningColor = if (dark) Color(0xFFB5F1BF) else Color(0xFF145C2E)
    val runningContainer = if (dark) Color(0xFF163C25) else Color(0xFFD9F5DF)
    val stoppedColor = if (dark) Color(0xFFFFB4AB) else Color(0xFF932C26)
    val stoppedContainer = if (dark) Color(0xFF542622) else Color(0xFFFFE2DE)
    val runningText = text(R.string.train_calendar_running)
    val stoppedText = text(R.string.train_calendar_stopped)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        operatingCalendarCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(40.dp))
                    } else {
                        val running = days?.dates?.get(date)
                        val status = when (running) {
                            true -> runningText
                            false -> stoppedText
                            null -> text(R.string.train_calendar_unknown)
                        }
                        val label = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                .testTag("train-calendar-day-$date")
                                .clearAndSetSemantics {
                                    contentDescription = label
                                    stateDescription = status
                                    selected = date == queryDate
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = when (running) {
                                true -> runningContainer
                                false -> stoppedContainer
                                null -> colors.surfaceContainerLow
                            },
                            contentColor = when (running) {
                                true -> runningColor
                                false -> stoppedColor
                                null -> colors.onSurfaceVariant
                            },
                            border = if (date == queryDate) BorderStroke(2.dp, colors.primary) else null,
                        ) {
                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
