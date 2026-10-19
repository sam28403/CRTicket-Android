package cc.samlab.ticket.ui.history

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

@Composable
internal fun HistoryPickerField(
    draft: HistoryDraft,
    key: String,
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        shape = MaterialTheme.shapes.large,
        isError = draft.error(key) != null,
        supportingText = draft.error(key)?.let { message -> { Text(message) } },
        modifier = modifier.fillMaxWidth().testTag("history-field-$key")
            .semantics {
                if (enabled) onClick(label) {
                    onOpen()
                    true
                }
            }
            .pointerInput(enabled, onOpen) {
                // Observe before the read-only text field consumes the picker tap.
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null && enabled) {
                        onOpen()
                    }
                }
            },
    )
}
