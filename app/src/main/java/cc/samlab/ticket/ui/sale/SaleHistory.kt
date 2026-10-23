package cc.samlab.ticket.ui.sale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun SaleHistory(
    history: List<String>,
    stations: Set<String>,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
) {
    // Keep outgoing labels until the exit transition finishes.
    var displayed by remember { mutableStateOf(history) }
    SideEffect { if (history.isNotEmpty()) displayed = history }
    AnimatedVisibility(
        visible = history.isNotEmpty(),
        enter = fadeIn(tween(220)) + expandVertically(tween(320, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(320, easing = FastOutSlowInEasing)),
        modifier = Modifier.testTag("sale-history"),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            displayed.filter { it in stations }.forEach { name ->
                OutlinedButton(onClick = { onSearch(name) }, enabled = history.isNotEmpty()) { Text(name) }
            }
            TextButton(onClick = onClear, enabled = history.isNotEmpty()) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}
