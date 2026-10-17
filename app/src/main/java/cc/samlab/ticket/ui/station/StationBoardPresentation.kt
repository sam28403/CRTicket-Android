package cc.samlab.ticket.ui.station

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keep each station's selected screen and exact list offsets while visiting details. */
internal class StationBoardPresentation {
    var selectedTab by mutableStateOf(StationTab.ALL)
    var follow by mutableStateOf(true)
    val grids = StationTab.entries.associateWith { LazyGridState() }
}
