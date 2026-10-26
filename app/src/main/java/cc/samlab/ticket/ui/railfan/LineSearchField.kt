package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineSearchField(
    input: String,
    lines: List<String>,
    onEdit: (String) -> Unit,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val matches = remember(lines, input) { matchingRailwayLines(lines, input) }
    fun select(line: String) {
        expanded = false
        focus.clearFocus()
        onSelect(line)
    }
    ExposedDropdownMenuBox(
        expanded = expanded && input.isNotBlank(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                onEdit(it)
                expanded = it.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("line-search"),
            label = { Text(stringResource(R.string.line_query_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (input.isNotEmpty()) IconButton(onClick = {
                    expanded = false
                    focus.clearFocus()
                    onClear()
                }) {
                    Icon(Icons.Default.Close, stringResource(R.string.vehicle_query_clear))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (matches.size == 1 || matches.firstOrNull() == input.trim()) select(matches.first())
                else expanded = true
            }),
        )
        ExposedDropdownMenu(
            expanded = expanded && input.isNotBlank(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp).testTag("line-candidates"),
        ) {
            if (matches.isEmpty()) DropdownMenuItem(
                text = { Text(stringResource(R.string.line_query_no_matches)) },
                onClick = {}, enabled = false,
            )
            matches.forEach { line ->
                DropdownMenuItem(text = { Text(line) }, onClick = { select(line) })
            }
        }
    }
}
