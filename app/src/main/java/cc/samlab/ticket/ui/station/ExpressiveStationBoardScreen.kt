package cc.samlab.ticket.ui.station

import android.net.Uri
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveStationBoardScreen(
    controller: NativeStationBoardController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    active: Boolean,
    embedded: Boolean = false,
) {
    val list = controller.listState
    val tab = controller.selectedTab
    val rows = controller.tabs[tab].orEmpty()
    val timing by rememberStationBoardTiming(rows, active)
    val index = timing.currentIndex
    LaunchedEffect(index, controller.follow, tab, controller.revision, active) {
        if (active && controller.follow && rows.isNotEmpty()) list.scrollToItem(index)
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
                TopAppBar(
                    title = { Text("车站大屏", fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                StationBoardSearch(controller)
                if (controller.loading) LinearWavyProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("station-loading"),
                )
                if (controller.error.isNotEmpty()) Text(
                    controller.error,
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = colors.error,
                )
                if (controller.date.isNotEmpty()) {
                    StationBoardActions(
                        total = if (tab == StationTab.MAP) controller.tabs[StationTab.ALL].orEmpty().size else rows.size,
                        follow = controller.follow,
                        onFollow = { controller.follow = it },
                        onLocate = {
                            controller.follow = true
                            scope.launch { if (rows.isNotEmpty()) list.animateScrollToItem(index) }
                        },
                        map = tab == StationTab.MAP,
                    )
                    StationBoardTabs(tab) { controller.selectedTab = it }
                    if (tab == StationTab.MAP) {
                        if (active) StationBoardMap(
                            controller.resultStation, dark,
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("station-board"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (rows.isEmpty()) item { Text("本站当天没有该类型的车次", Modifier.padding(24.dp)) }
                        items(rows, key = { it.id }, contentType = { "station-train" }) { row ->
                            StationBoardItem(
                                row, tab, timing.highlight(row.id), dark,
                                onTrain = {
                                    controller.follow = false
                                    onNavigate(row.route)
                                },
                                onStation = {
                                    controller.follow = false
                                    onNavigate("/station?station=${Uri.encode(it)}")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
