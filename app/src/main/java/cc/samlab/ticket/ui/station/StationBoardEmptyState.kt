package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun StationBoardEmptyState(station: String, modifier: Modifier = Modifier) {
    val textResources = rememberAppText()
    Column(
        modifier.fillMaxWidth().padding(32.dp).testTag("station-no-trains"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_home_train), contentDescription = null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary,
        )
        Text(textResources(R.string.station_board_empty), style = MaterialTheme.typography.titleLarge)
        Text(
            textResources(R.string.station_board_empty_description, station),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
