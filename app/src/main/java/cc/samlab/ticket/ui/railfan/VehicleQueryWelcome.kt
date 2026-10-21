package cc.samlab.ticket.ui.railfan

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VehicleQueryWelcome(
    history: List<String>,
    active: Boolean,
    modifier: Modifier,
    onClearHistory: () -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp).testTag("vehicle-welcome"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.vehicle_query_history),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalIconButton(
                onClick = onClearHistory,
                enabled = history.isNotEmpty(),
                modifier = Modifier.testTag("vehicle-history-clear"),
            ) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.vehicle_query_history_clear))
            }
        }
        AnimatedContent(
            targetState = history,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(220, 100)) + expandVertically(tween(350))) togetherWith
                    (fadeOut(tween(180)) + shrinkVertically(tween(350)))
            },
            label = "vehicle-search-history",
        ) { entries ->
            if (entries.isEmpty()) Text(
                stringResource(R.string.vehicle_query_history_empty),
                Modifier.padding(vertical = 8.dp).testTag("vehicle-history-empty"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            ) else FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { keyword ->
                    SuggestionChip(
                        onClick = { onSearch(keyword) },
                        label = { Text(keyword) },
                        modifier = Modifier.testTag("vehicle-recent:$keyword"),
                    )
                }
            }
        }
        VehicleHeadCarousel(active, Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}
