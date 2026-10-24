package cc.samlab.ticket.ui.station

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.samlab.ticket.ui.maps.MapSurface

@Composable
internal fun StationBoardMap(
    station: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onChooseSource: (() -> Unit)? = null,
) {
    MapSurface(
        station = station,
        dark = dark,
        modifier = modifier,
        onChooseSource = onChooseSource,
    )
}
