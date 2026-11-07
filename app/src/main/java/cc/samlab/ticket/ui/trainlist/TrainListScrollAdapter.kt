package cc.samlab.ticket.ui.trainlist

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.channels.Channel
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.Predicate

internal data class TrainScrollLayout(
    val itemCount: Int,
    val itemsPerLine: Int,
    val firstIndex: Int,
    val firstOffset: Int,
    val lineSize: Int,
    val spacing: Int,
    val viewport: Int,
    val padding: Int,
    val canScrollForward: Boolean,
) {
    val stride get() = (lineSize + spacing).coerceAtLeast(1)
    val lineCount get() = (itemCount + itemsPerLine - 1) / itemsPerLine
}

/** Connect AndroidFastScroll to lazy Compose content without measuring offscreen rows. */
internal class TrainListScrollAdapter(private val host: ViewGroup) : FastScroller.ViewHelper {
    var layout: () -> TrainScrollLayout? = { null }
    var touch: Predicate<MotionEvent>? = null
    var bounds = Rect.Zero
    val requests = Channel<Pair<Int, Int>>(Channel.CONFLATED)
    private var scrollChanged: Runnable? = null

    override fun addOnPreDrawListener(listener: Runnable) {
        host.viewTreeObserver.addOnPreDrawListener { listener.run(); true }
    }

    override fun addOnScrollChangedListener(listener: Runnable) { scrollChanged = listener }

    override fun addOnTouchEventListener(listener: Predicate<MotionEvent>) { touch = listener }

    private fun maximumOffset(info: TrainScrollLayout): Int =
        (info.lineCount.toLong() * info.stride - info.spacing + info.padding - info.viewport)
            .coerceIn(0L, Int.MAX_VALUE.toLong() - host.height).toInt()

    override fun getScrollRange(): Int = host.height + (layout()?.let(::maximumOffset) ?: 0)

    override fun getScrollOffset(): Int {
        val info = layout() ?: return 0
        val maximum = maximumOffset(info)
        if (!info.canScrollForward) return maximum
        val firstLine = info.firstIndex / info.itemsPerLine
        return (firstLine.toLong() * info.stride + info.firstOffset)
            .coerceIn(0L, maximum.toLong()).toInt()
    }

    override fun scrollTo(offset: Int) {
        val info = layout() ?: return
        if (info.itemCount == 0) return
        val maximum = maximumOffset(info)
        val target = offset.coerceIn(0, maximum)
        if (target == maximum) {
            requests.trySend((info.itemCount - 1) to 0)
        } else {
            val line = (target / info.stride).coerceAtMost(info.lineCount - 1)
            requests.trySend((line * info.itemsPerLine) to (target % info.stride))
        }
    }

    fun changed() {
        scrollChanged?.run()
        host.invalidate()
    }
}

@Composable
internal fun BindTrainListScroll(adapter: TrainListScrollAdapter, list: LazyListState) {
    BindTrainScroll(adapter, list, list::scrollToItem) {
        val info = list.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return@BindTrainScroll null
        TrainScrollLayout(
            itemCount = info.totalItemsCount,
            itemsPerLine = 1,
            firstIndex = list.firstVisibleItemIndex,
            firstOffset = list.firstVisibleItemScrollOffset,
            lineSize = first.size,
            spacing = info.mainAxisItemSpacing,
            viewport = info.viewportEndOffset - info.viewportStartOffset,
            padding = info.afterContentPadding,
            canScrollForward = list.canScrollForward,
        )
    }
}

@Composable
internal fun BindTrainListScroll(adapter: TrainListScrollAdapter, grid: LazyGridState) {
    BindTrainScroll(adapter, grid, grid::scrollToItem) {
        val info = grid.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return@BindTrainScroll null
        val lineSize = info.visibleItemsInfo
            .filter { it.row == first.row }
            .maxOf { it.size.height }
        TrainScrollLayout(
            itemCount = info.totalItemsCount,
            itemsPerLine = info.maxSpan,
            firstIndex = grid.firstVisibleItemIndex,
            firstOffset = grid.firstVisibleItemScrollOffset,
            lineSize = lineSize,
            spacing = info.mainAxisItemSpacing,
            viewport = info.viewportEndOffset - info.viewportStartOffset,
            padding = info.beforeContentPadding + info.afterContentPadding,
            canScrollForward = grid.canScrollForward,
        )
    }
}

@Composable
private fun BindTrainScroll(
    adapter: TrainListScrollAdapter,
    state: Any,
    scrollToItem: suspend (Int, Int) -> Unit,
    layout: () -> TrainScrollLayout?,
) {
    SideEffect { adapter.layout = layout }
    LaunchedEffect(adapter, state) {
        for ((index, offset) in adapter.requests) {
            val count = adapter.layout()?.itemCount ?: 0
            if (count > 0) scrollToItem(index.coerceIn(0, count - 1), offset)
        }
    }
    LaunchedEffect(adapter, state) {
        snapshotFlow { layout() }.collect { adapter.changed() }
    }
}
