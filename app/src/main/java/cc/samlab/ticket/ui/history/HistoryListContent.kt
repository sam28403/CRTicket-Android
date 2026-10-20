package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.trainlist.TrainListRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryListContent(
    controller: NativeHistoryController,
    list: LazyListState,
    rows: List<HistoryRecord>,
    visible: Boolean,
) {
    val textResources = rememberAppText()
    val colors = MaterialTheme.colorScheme
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = controller.loading,
        onRefresh = { controller.action("refresh") },
        state = pull,
        modifier = Modifier.fillMaxSize().testTag("history-pull").semantics {
            stateDescription = if (controller.loading) textResources(R.string.history_refreshing) else textResources(R.string.history_loaded)
        },
        indicator = { TrainListRefreshIndicator(pull, controller.loading, textResources(R.string.history_refreshing)) },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("history-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                Surface(shape = RoundedCornerShape(32.dp), color = colors.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            textResources(
                                R.string.history_summary,
                                controller.rows.size,
                                historyAmount(controller.distance),
                                historyAmount(controller.money),
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { controller.action("create") }) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(textResources(R.string.history_create))
                        }
                    }
                }
            }
            if (controller.error.isNotEmpty()) item(key = "error") {
                Column(Modifier.padding(16.dp)) {
                    Text(controller.error, color = colors.error)
                    TextButton(onClick = { controller.action("refresh") }) { Text(textResources(R.string.action_retry)) }
                }
            }
            if (!controller.loading && controller.error.isEmpty() && rows.isEmpty()) item(key = "empty") {
                ExpressiveEmptyState(
                    message = stringResource(R.string.history_empty),
                    visible = visible,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        .testTag("history-empty"),
                    artworkModifier = Modifier.testTag("history-empty-artwork"),
                )
            }
            itemsIndexed(rows, key = { index, row -> "${row.id}:$index" }) { _, row ->
                HistoryRecordCard(row, controller::action)
            }
        }
    }
}
