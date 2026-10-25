package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> FareSelector(
    label: Int,
    values: List<T>,
    selected: T,
    title: (T) -> Int,
    tag: String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = stringResource(title(selected)),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag("$tag-selector"),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(title(value))) },
                    onClick = { onSelect(value); expanded = false },
                    modifier = Modifier.testTag("$tag-$value"),
                )
            }
        }
    }
}
