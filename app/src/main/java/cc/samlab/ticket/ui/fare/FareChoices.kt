package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> FareChoices(
    label: Int,
    values: List<T>,
    selected: T,
    title: (T) -> Int,
    tag: String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            values.forEachIndexed { index, value ->
                ToggleButton(
                    checked = selected == value,
                    onCheckedChange = { onSelect(value) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("$tag-$value"),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        values.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(title(value)), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
