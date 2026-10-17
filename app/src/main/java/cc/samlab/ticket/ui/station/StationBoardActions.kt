package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun StationBoardActions(
    total: Int,
    follow: Boolean,
    onFollow: (Boolean) -> Unit,
    onLocate: () -> Unit,
    map: Boolean,
    modifier: Modifier = Modifier,
) {
    // Wrap at large font scales instead of squeezing labels or touch targets.
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("station-actions"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("共 $total 趟", style = MaterialTheme.typography.labelLarge)
        if (!map) {
            TextButton(
                onClick = onLocate,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("定位到当前时间", style = MaterialTheme.typography.labelMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("跟随当前时间", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = follow,
                    onCheckedChange = onFollow,
                    modifier = Modifier.testTag("station-follow"),
                )
            }
        }
    }
}
