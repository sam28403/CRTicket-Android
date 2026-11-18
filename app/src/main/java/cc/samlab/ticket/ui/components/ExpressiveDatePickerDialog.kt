package cc.samlab.ticket.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveDatePickerDialog(
    state: DatePickerState,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    wheelRange: ClosedRange<LocalDate>? = null,
) {
    val text = rememberAppText()
    val colors = DatePickerDefaults.colors(containerColor = TimePickerDialogDefaults.vibrantContainerColor)
    var showWheels by rememberSaveable { mutableStateOf(false) }
    var wheelsMoving by remember { mutableStateOf(false) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = TimePickerDialogDefaults.vibrantShape,
        colors = colors,
        confirmButton = {
            FilledTonalButton(
                onClick = { state.selectedDateMillis?.let(onSelect) },
                enabled = !wheelsMoving && state.selectedDateMillis != null,
            ) { Text(text(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text(R.string.action_cancel)) }
        },
    ) {
        val formatter = remember { DatePickerDefaults.dateFormatter() }
        val displayMode = if (showWheels) DisplayMode.Input else DisplayMode.Picker
        val headline: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = LocalTextStyle.current
                val selected = state.selectedDateMillis
                ProvideTextStyle(if (selected == null) style else style.copy(fontFamily = projectTitleFont)) {
                    DatePickerDefaults.DatePickerHeadline(
                        selectedDateMillis = selected,
                        displayMode = displayMode,
                        dateFormatter = formatter,
                        modifier = Modifier.weight(1f),
                        contentColor = colors.headlineContentColor,
                    )
                }
                IconButton(
                    enabled = !wheelsMoving,
                    onClick = {
                        if (showWheels) {
                            state.selectedDateMillis?.let { state.displayedMonthMillis = it }
                        }
                        showWheels = !showWheels
                    },
                    modifier = Modifier.testTag("date-picker-wheel-toggle"),
                ) {
                    Icon(
                        imageVector = if (showWheels) Icons.Default.DateRange else Icons.Default.SwapVert,
                        contentDescription = text(
                            if (showWheels) R.string.date_picker_calendar else R.string.date_picker_wheels,
                        ),
                        tint = colors.titleContentColor,
                    )
                }
            }
        }
        if (showWheels) {
            Column {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    DatePickerDefaults.DatePickerTitle(
                        displayMode = displayMode,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp),
                        contentColor = colors.titleContentColor,
                    )
                }
                ProvideTextStyle(MaterialTheme.typography.headlineLarge, content = headline)
                HorizontalDivider(color = colors.dividerColor)
                DateWheelFields(state, wheelRange, onScrollingChange = { wheelsMoving = it }) { date ->
                    state.selectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                }
            }
        } else {
            DatePicker(
                state = state,
                colors = colors,
                dateFormatter = formatter,
                headline = headline,
                showModeToggle = false,
            )
        }
    }
}
