package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StationBoardToolbar(
    controller: StationBoardState,
    total: Int,
) {
    val tab = controller.selectedTab
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        StationBoardActions(
            total = total,
            follow = controller.follow,
            onFollow = { controller.follow = it },
            map = tab == StationTab.MAP,
            modifier = modifier,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationBoardTabs(tab, Modifier.weight(1f)) { controller.selectedTab = it }
                actions(Modifier.weight(1f))
            }
        } else Column {
            actions(Modifier)
            StationBoardTabs(tab) { controller.selectedTab = it }
        }
    }
}
