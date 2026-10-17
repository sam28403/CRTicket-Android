package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.ExpressiveEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonitorResultsContent(
    controller: NativeTicketMonitorController,
    grid: LazyGridState,
    appBar: TopAppBarState,
    pull: PullToRefreshState,
    active: Boolean,
) {
    var armed by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = controller.loading,
        onRefresh = { controller.action("refresh") },
        state = pull,
        enabled = armed && !controller.state.optBoolean("restoring"),
        modifier = Modifier.fillMaxSize().testTag("monitor-pull")
            .pointerInput(grid, appBar) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    armed = !grid.canScrollBackward && appBar.heightOffset >= -0.5f
                    waitForUpOrCancellation(pass = PointerEventPass.Final)
                }
            },
        indicator = {},
    ) {
        val state = controller.state
        val rows = state.optJSONArray("filtered")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp),
            state = grid,
            modifier = Modifier.fillMaxSize().testTag("monitor-results"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { MonitorControls(controller) }
            if (state.optString("error").isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(state.optString("error"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
            if ((rows?.length() ?: 0) > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${rows!!.length()} 趟 · 关注席别有票 ${state.optInt("ticketCount")} 趟",
                        Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(rows!!.length(), key = { rows.getJSONObject(it).optString("id", "$it") }) {
                    MonitorTicketCard(rows.getJSONObject(it), state.optJSONArray("columns"))
                }
            } else item(span = { GridItemSpan(maxLineSpan) }) {
                ExpressiveEmptyState(
                    message = when {
                        controller.loading -> "正在查询…"
                        state.optString("queriedAt").isEmpty() -> "请选择车站和日期，开始查询"
                        else -> "没有符合条件的车次"
                    },
                    visible = active,
                    modifier = Modifier.fillMaxWidth().testTag("monitor-empty"),
                    artworkModifier = Modifier.testTag("monitor-empty-artwork"),
                )
            }
        }
    }
}
