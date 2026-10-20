package cc.samlab.ticket.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import java.time.YearMonth

/** Month-only selection, using the same native Expressive theme as the page. */
@Composable
internal fun StatisticsMonthPicker(
    initial: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val textResources = rememberAppText()
    val value = remember(initial) { YearMonth.parse(initial) }
    var year by remember(initial) { mutableIntStateOf(value.year) }
    var month by remember(initial) { mutableIntStateOf(value.monthValue) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        modifier = Modifier.testTag("statistics-month-picker"),
        onDismissRequest = onDismiss,
        title = { Text(textResources(R.string.statistics_choose_month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { year-- }, enabled = year > 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, textResources(R.string.action_previous_year))
                    }
                    Text(
                        textResources(
                            R.string.calendar_year,
                            year,
                        ), Modifier.weight(1f).testTag("statistics-month-year"),
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    )
                    FilledTonalIconButton(onClick = { year++ }, enabled = year < 9999) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, textResources(R.string.action_next_year))
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        months.forEach { number ->
                            FilledTonalButton(
                                onClick = { month = number },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("statistics-month-$number")
                                    .semantics { selected = month == number },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (month == number) colors.primaryContainer
                                        else colors.surfaceContainerHigh,
                                    contentColor = if (month == number) colors.onPrimaryContainer
                                        else colors.onSurfaceVariant,
                                ),
                            ) { Text(textResources(R.string.calendar_month, number)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(YearMonth.of(year, month).toString().padStart(7, '0')) }) {
                Text(textResources(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textResources(R.string.action_cancel)) } },
    )
}
