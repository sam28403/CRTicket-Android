package cc.samlab.ticket.ui.railfan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun VehicleSearchField(
    input: String,
    invalid: Boolean,
    onEdit: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val focus = LocalFocusManager.current
    fun submit() {
        focus.clearFocus()
        onSearch()
    }
    OutlinedTextField(
        value = input,
        onValueChange = onEdit,
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp).testTag("vehicle-search"),
        label = { Text(stringResource(R.string.vehicle_query_hint)) },
        placeholder = { Text("G1 / 380B-3626") },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.vehicle_query_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        leadingIcon = { Icon(Icons.Default.Train, null) },
        trailingIcon = {
            Row {
                if (input.isNotEmpty()) IconButton(onClick = {
                    focus.clearFocus()
                    onClear()
                }) {
                    Icon(Icons.Default.Close, stringResource(R.string.vehicle_query_clear))
                }
                IconButton(onClick = ::submit, enabled = input.isNotBlank()) {
                    Icon(Icons.Default.Search, stringResource(R.string.action_query))
                }
            }
        },
    )
}

@Composable
internal fun VehicleQueryMessage(@StringRes message: Int, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.vehicle_query_retry))
        }
    }
}
