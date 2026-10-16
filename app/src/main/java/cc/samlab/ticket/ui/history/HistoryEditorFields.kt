package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryTextField(
    draft: HistoryDraft,
    key: String,
    label: String,
    placeholder: String = "",
    numeric: Boolean = false,
    multiline: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = draft.value(key),
        onValueChange = { draft.fields[key] = it },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth().testTag("history-field-$key"),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryChoiceField(
    draft: HistoryDraft,
    key: String,
    label: String,
    choices: List<HistoryChoice>,
    enabled: Boolean,
    onSelect: (String) -> Unit = { draft.fields[key] = it },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = choices.firstOrNull { it.value == draft.value(key) }?.label ?: draft.value(key),
            onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag("history-field-$key"),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) }, enabled = !choice.disabled,
                    onClick = {
                        onSelect(choice.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun HistorySwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.testTag("history-switch-$label"))
    }
}
