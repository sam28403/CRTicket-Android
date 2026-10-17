package cc.samlab.ticket.ui.statistics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ExpressiveEmptyState

@Composable
internal fun StatisticsEmptyState(visible: Boolean) {
    ExpressiveEmptyState(
        message = stringResource(R.string.statistics_empty),
        visible = visible,
        modifier = Modifier.fillMaxSize().testTag("statistics-empty")
            .verticalScroll(rememberScrollState()),
        artworkModifier = Modifier.testTag("statistics-empty-artwork"),
    )
}
