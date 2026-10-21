package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.AppSwitch
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun StationBoardActions(
    total: Int,
    follow: Boolean,
    onFollow: (Boolean) -> Unit,
    map: Boolean,
    modifier: Modifier = Modifier,
) {
    val textResources = rememberAppText()
    // Wrap at large font scales instead of squeezing labels or touch targets.
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("station-actions"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(textResources(R.string.train_list_total, total), style = MaterialTheme.typography.labelLarge)
        if (!map) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(textResources(R.string.station_board_follow_now), style = MaterialTheme.typography.labelMedium)
                AppSwitch(
                    checked = follow,
                    onCheckedChange = onFollow,
                    modifier = Modifier.testTag("station-follow"),
                )
            }
        }
    }
}
