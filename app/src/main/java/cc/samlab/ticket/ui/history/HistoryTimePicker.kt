package cc.samlab.ticket.ui.history

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.VibrantTimePickerDialog
import androidx.compose.material3.isInputValid
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.components.rememberPickerWheelState
import cc.samlab.ticket.ui.home.projectTitleFont
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryTimePicker(
    time: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val textResources = rememberAppText()
    val parts = time.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true,
    )
    var scrollMode by rememberSaveable { mutableStateOf(true) }
    val hours = if (scrollMode) rememberPickerWheelState(24, state.hour) else null
    val minutes = if (scrollMode) rememberPickerWheelState(60, state.minute) else null
    val displayMode = if (scrollMode) {
        TimePickerDisplayMode.Scroll
    } else {
        TimePickerDisplayMode.Input
    }

    VibrantTimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = scrollMode || state.isInputValid,
                onClick = {
                    val hour = hours?.selectedOption ?: state.hour
                    val minute = minutes?.selectedOption ?: state.minute
                    onSelect("%02d:%02d".format(Locale.ROOT, hour, minute))
                },
            ) { Text(textResources(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textResources(R.string.action_cancel)) } },
        modeToggleButton = {
            TimePickerDialogDefaults.ScrollDisplayModeToggle(
                onDisplayModeChange = {
                    if (hours != null && minutes != null) {
                        state.hour = hours.selectedOption
                        state.minute = minutes.selectedOption
                    }
                    scrollMode = !scrollMode
                },
                displayMode = displayMode,
                modifier = Modifier.testTag("history-time-mode-toggle"),
            )
        },
    ) {
        val typography = MaterialTheme.typography
        MaterialTheme(
            motionScheme = MaterialTheme.motionScheme,
            typography = typography.copy(
                displayLarge = typography.displayLarge.copy(fontFamily = projectTitleFont),
                displayMedium = typography.displayMedium.copy(fontFamily = projectTitleFont),
                displayLargeEmphasized = typography.displayLargeEmphasized.copy(fontFamily = projectTitleFont),
            ),
        ) {
            if (hours != null && minutes != null) {
                HistoryTimeWheels(hours, minutes)
            } else {
                TimeInput(
                    state = state,
                    shapes = TimePickerDefaults.shapes(),
                    modifier = Modifier.testTag("history-time-input"),
                )
            }
        }
    }
}
