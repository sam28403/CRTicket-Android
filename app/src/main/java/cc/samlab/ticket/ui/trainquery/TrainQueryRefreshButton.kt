package cc.samlab.ticket.ui.trainquery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun TrainQueryRefreshButton(enabled: Boolean, onRefresh: () -> Unit) {
    val text = rememberAppText()
    IconButton(
        onClick = onRefresh,
        enabled = enabled,
        modifier = Modifier.testTag("train-query-refresh"),
    ) {
        Icon(Icons.Default.Refresh, text(R.string.station_refresh))
    }
}
