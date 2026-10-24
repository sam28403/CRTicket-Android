package cc.samlab.ticket.ui.station

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.QueryResultEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveStationBoardScreen(
    controller: StationBoardState,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    active: Boolean,
    embedded: Boolean = false,
) {
    val list = controller.gridState
    val tab = controller.selectedTab
    val rows = controller.tabs[tab].orEmpty()
    val hasTrains = controller.tabs[StationTab.ALL].orEmpty().isNotEmpty()
    val failed = !controller.loading && controller.error.isNotEmpty() && !hasTrains
    val textResources = rememberAppText()
    val timing by rememberStationBoardTiming(rows, active)
    val index = timing.currentIndex
    LaunchedEffect(list, index, controller.follow, controller.revision, active) {
        if (active && controller.follow && rows.isNotEmpty()) list.animateToCurrentTrain(index)
    }
    LaunchedEffect(list, controller) {
        list.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) controller.follow = false
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize().testTag("station-screen"), color = colors.surfaceContainer) {
            Column {
                Column(Modifier.testTag("station-controls")) {
                    StationBoardHeader(controller, embedded, onBack)
                    if (controller.loading) LinearWavyProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("station-loading"),
                    )
                    if (controller.error.isNotEmpty() && hasTrains) Text(
                        controller.error,
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        color = colors.error,
                    )
                    if (controller.date.isNotEmpty()) {
                        StationBoardToolbar(
                            controller = controller,
                            total = if (tab == StationTab.MAP) {
                                controller.tabs[StationTab.ALL].orEmpty().size
                            } else rows.size,
                        )
                    }
                }
                if (failed) {
                    QueryResultEmptyState(
                        message = controller.error,
                        active = active,
                        modifier = Modifier.weight(1f).testTag("station-query-error"),
                        onRetry = controller::request,
                    )
                } else if (controller.date.isNotEmpty()) {
                    if (tab == StationTab.MAP) {
                        if (active) StationBoardMap(
                            controller.resultStation, dark,
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("station-map"),
                        )
                    } else if (!hasTrains && !controller.loading) {
                        StationBoardEmptyState(
                            controller.resultStation, Modifier.weight(1f), active,
                        )
                    } else if (rows.isEmpty() && !controller.loading) {
                        QueryResultEmptyState(
                            message = textResources(R.string.station_board_type_empty),
                            active = active,
                            modifier = Modifier.weight(1f).testTag("station-type-empty"),
                        )
                    } else StationBoardGrid(
                        controller, rows, timing, dark, onNavigate,
                        Modifier.weight(1f),
                    )
                } else if (!controller.loading) {
                    StationBoardDiscovery(controller, onNavigate, Modifier.weight(1f))
                }
            }
        }
    }
}
