package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.trainlist.TrainListRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainQueryRefreshBox(
    offline: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (offline) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val pull = rememberPullToRefreshState()
    val text = rememberAppText()
    Box(
        Modifier.fillMaxSize().testTag("train-query-pull").pullToRefresh(
            isRefreshing = refreshing,
            state = pull,
            enabled = enabled,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        TrainListRefreshIndicator(pull, refreshing, text(R.string.status_loading))
    }
}
