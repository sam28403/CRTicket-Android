package cc.samlab.ticket.ui.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun TrainComparisonSearch(
    input: String,
    onEdit: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                val value = normalizeComparisonNumber(it)
                if (validComparisonInput(value)) onEdit(value)
                else Toast.makeText(
                    context, R.string.train_comparison_invalid, Toast.LENGTH_SHORT,
                ).show()
            },
            placeholder = { Text(stringResource(R.string.train_comparison_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { if (enabled) onSearch() }),
            modifier = Modifier.weight(1f).testTag("train-comparison-query"),
        )
        Button(onClick = onSearch, enabled = enabled, modifier = Modifier.testTag("train-comparison-search")) {
            Text(stringResource(R.string.sale_search))
        }
    }
}
