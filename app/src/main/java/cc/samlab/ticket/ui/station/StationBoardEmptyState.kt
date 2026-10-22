package cc.samlab.ticket.ui.station

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.QueryResultEmptyState

@Composable
internal fun StationBoardEmptyState(
    station: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val textResources = rememberAppText()
    QueryResultEmptyState(
        message = textResources(R.string.station_board_empty),
        active = active,
        modifier = modifier.testTag("station-no-trains"),
        description = textResources(R.string.station_board_empty_description, station),
    )
}
