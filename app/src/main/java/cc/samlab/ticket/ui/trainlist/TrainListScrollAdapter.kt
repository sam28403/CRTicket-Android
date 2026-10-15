package cc.samlab.ticket.ui.trainlist

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.channels.Channel
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.Predicate

/** Connect AndroidFastScroll to LazyColumn without materializing or measuring all rows. */
internal class TrainListScrollAdapter(private val host: ViewGroup) : FastScroller.ViewHelper {
    var list: LazyListState? = null
    var touch: Predicate<MotionEvent>? = null
    var bounds = Rect.Zero
    val requests = Channel<Pair<Int, Int>>(Channel.CONFLATED)
    private var scrollChanged: Runnable? = null

    override fun addOnPreDrawListener(listener: Runnable) {
        host.viewTreeObserver.addOnPreDrawListener { listener.run(); true }
    }

    override fun addOnScrollChangedListener(listener: Runnable) { scrollChanged = listener }

    override fun addOnTouchEventListener(listener: Predicate<MotionEvent>) { touch = listener }

    private fun stride(): Int {
        val info = list?.layoutInfo ?: return 1
        return ((info.visibleItemsInfo.firstOrNull()?.size ?: 1) + info.mainAxisItemSpacing)
            .coerceAtLeast(1)
    }

    private fun maximumOffset(): Int {
        val info = list?.layoutInfo ?: return 0
        if (info.visibleItemsInfo.isEmpty()) return 0
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        return (info.totalItemsCount.toLong() * stride() - info.mainAxisItemSpacing +
            info.afterContentPadding - viewport).coerceIn(0L, Int.MAX_VALUE.toLong() - host.height).toInt()
    }

    override fun getScrollRange(): Int = host.height + maximumOffset()

    override fun getScrollOffset(): Int {
        val state = list ?: return 0
        val maximum = maximumOffset()
        if (!state.canScrollForward) return maximum
        return (state.firstVisibleItemIndex.toLong() * stride() + state.firstVisibleItemScrollOffset)
            .coerceIn(0L, maximum.toLong()).toInt()
    }

    override fun scrollTo(offset: Int) {
        val state = list ?: return
        val count = state.layoutInfo.totalItemsCount
        if (count == 0) return
        val target = offset.coerceIn(0, maximumOffset())
        if (target == maximumOffset()) requests.trySend((count - 1) to 0)
        else requests.trySend((target / stride()).coerceIn(0, count - 1) to (target % stride()))
    }

    fun changed() {
        scrollChanged?.run()
        host.invalidate()
    }
}

@Composable
internal fun BindTrainListScroll(adapter: TrainListScrollAdapter, list: LazyListState) {
    SideEffect { adapter.list = list }
    LaunchedEffect(adapter, list) {
        for ((index, offset) in adapter.requests) {
            val count = list.layoutInfo.totalItemsCount
            if (count > 0) list.scrollToItem(index.coerceIn(0, count - 1), offset)
        }
    }
    LaunchedEffect(adapter, list) {
        snapshotFlow {
            Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.layoutInfo.totalItemsCount)
        }.collect { adapter.changed() }
    }
}
