package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.QueryResultEmptyState
import cc.samlab.ticket.ui.trainlist.TrainListRefreshIndicator
import cc.samlab.ticket.ui.trainlist.TrainListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleQueryResults(
    query: VehicleSearch,
    rows: List<VehicleHistory>,
    trainRows: Map<String, TrainListRow>,
    loading: Boolean,
    refreshing: Boolean,
    error: Boolean,
    modifier: Modifier,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onVehicle: (String) -> Unit,
    onNavigate: (String) -> Unit,
    active: Boolean = true,
) {
    val pull = rememberPullToRefreshState()
    val description = stringResource(R.string.vehicle_query_refresh)
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!loading) onRefresh() },
        state = pull,
        modifier = modifier.fillMaxWidth().testTag("vehicle-refresh").semantics {
            customActions = listOf(CustomAccessibilityAction(description) {
                if (!loading) onRefresh()
                true
            })
        },
        indicator = {
            TrainListRefreshIndicator(
                pull, refreshing, stringResource(R.string.vehicle_query_refreshing),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (loading && !refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error && rows.isNotEmpty()) {
                VehicleQueryMessage(R.string.vehicle_query_error, onRetry)
            }
            if (rows.isNotEmpty()) VehicleHistoryGrid(
                rows, query.byTrain, trainRows, Modifier.weight(1f), onVehicle, onNavigate,
            ) else if (!loading) QueryResultEmptyState(
                message = stringResource(
                    if (error) R.string.vehicle_query_error else R.string.vehicle_query_empty,
                ),
                active = active,
                modifier = Modifier.weight(1f).testTag("vehicle-query-empty"),
                onRetry = if (error) onRetry else null,
            )
        }
    }
}
