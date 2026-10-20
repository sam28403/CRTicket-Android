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
import cc.samlab.ticket.ui.components.AppSwitch

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
    val decimal = numeric && key == "price" && !draft.credit
    OutlinedTextField(
        value = draft.value(key),
        onValueChange = {
            if (!numeric || acceptsTicketNumberInput(it, decimal)) draft.fields[key] = it
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth().testTag("history-field-$key"),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        isError = draft.error(key) != null,
        supportingText = draft.error(key)?.let { message -> { Text(message) } },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                decimal -> KeyboardType.Decimal
                numeric -> KeyboardType.Number
                else -> KeyboardType.Text
            },
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
            isError = draft.error(key) != null,
            supportingText = draft.error(key)?.let { message -> { Text(message) } },
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
        AppSwitch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.testTag("history-switch-$label"),
        )
    }
}
