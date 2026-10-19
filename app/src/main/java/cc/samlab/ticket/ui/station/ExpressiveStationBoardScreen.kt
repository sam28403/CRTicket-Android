package cc.samlab.ticket.ui.station

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.homeColorScheme
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize().testTag("station-screen"), color = colors.surfaceContainer) {
            Column {
                Column(Modifier.testTag("station-controls")) {
                    StationBoardHeader(controller, embedded, onBack)
                    if (controller.loading) LinearWavyProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("station-loading"),
                    )
                    if (controller.error.isNotEmpty()) Text(
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
                            onLocate = {
                                val alreadyFollowing = controller.follow
                                controller.follow = true
                                if (alreadyFollowing && rows.isNotEmpty()) {
                                    scope.launch { list.animateToCurrentTrain(index) }
                                }
                            },
                        )
                    }
                }
                if (controller.date.isNotEmpty()) {
                    if (tab == StationTab.MAP) {
                        if (active) StationBoardMap(
                            controller.resultStation, dark,
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else StationBoardGrid(
                        controller, rows, timing, dark, onNavigate,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
