package cc.samlab.ticket.ui.station

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun StationBoardGrid(
    controller: StationBoardState,
    rows: List<StationBoardRow>,
    timing: StationBoardTiming,
    dark: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textResources = rememberAppText()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        state = controller.gridState,
        modifier = modifier.fillMaxWidth().testTag("station-board"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(textResources(R.string.station_board_type_empty), Modifier.padding(24.dp))
        }
        items(rows, key = { it.id }, contentType = { "station-train" }) { row ->
            StationBoardItem(
                row, controller.selectedTab, timing.highlight(row.id), dark,
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
