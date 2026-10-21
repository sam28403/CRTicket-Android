package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun RoutingQueryResults(
    controller: RoutingQueryController,
    modifier: Modifier,
    onNavigate: (String) -> Unit,
) {
    key(controller.submitted, controller.groups) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp),
            modifier = modifier.fillMaxWidth().testTag("routing-results"),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            controller.groups.forEach { group ->
                if (group.model.isNotBlank()) item(
                    key = "group:${group.id}", span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        group.model,
                        Modifier.padding(4.dp, 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                itemsIndexed(group.trains, key = { position, _ -> "${group.id}:$position" }) { _, train ->
                    RoutingTrainCard(train, controller.date, onNavigate)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { RoutingDisclaimer() }
        }
    }
}
