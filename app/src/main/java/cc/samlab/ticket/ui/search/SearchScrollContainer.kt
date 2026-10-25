package cc.samlab.ticket.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.ui.trainlist.BindTrainListScroll
import cc.samlab.ticket.ui.trainlist.TrainListScrollView

@Composable
internal fun SearchScrollContainer(
    list: LazyListState,
    content: @Composable (TrainListScrollView) -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    AndroidView(
        factory = { TrainListScrollView(it) },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.disposeContent() },
        update = { scroll ->
            scroll.setContent {
                BindTrainListScroll(scroll.adapter, list)
                currentContent(scroll)
            }
        },
    )
}
