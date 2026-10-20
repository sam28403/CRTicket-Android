package cc.samlab.ticket.ui.history

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
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
    var pickerMode by rememberSaveable { mutableStateOf(true) }
    val clockFits = LocalConfiguration.current.screenHeightDp.dp >
        TimePickerDialogDefaults.MinHeightForTimePicker
    val displayMode = if (pickerMode && clockFits) {
        TimePickerDisplayMode.Picker
    } else {
        TimePickerDisplayMode.Input
    }

    VibrantTimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.isInputValid,
                onClick = { onSelect("%02d:%02d".format(Locale.ROOT, state.hour, state.minute)) },
            ) { Text(textResources(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textResources(R.string.action_cancel)) } },
        modeToggleButton = {
            if (clockFits) {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = { pickerMode = !pickerMode },
                    displayMode = displayMode,
                    modifier = Modifier.testTag("history-time-mode-toggle"),
                )
            }
        },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) {
            TimePicker(
                state = state,
                shapes = TimePickerDefaults.shapes(),
                modifier = Modifier.testTag("history-time-clock"),
            )
        } else {
            TimeInput(
                state = state,
                shapes = TimePickerDefaults.shapes(),
                modifier = Modifier.testTag("history-time-input"),
            )
        }
    }
}
