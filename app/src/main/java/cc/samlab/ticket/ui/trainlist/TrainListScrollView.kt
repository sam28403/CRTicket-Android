package cc.samlab.ticket.ui.trainlist

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import me.zhanghai.android.fastscroll.FastScrollerBuilder

/** LibChecker's actual MD2 fast-scroll library, drawn over the existing Compose content. */
internal class TrainListScrollView(context: Context) : FrameLayout(
    ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar),
) {
    val adapter = TrainListScrollAdapter(this)
    private val content = ComposeView(this.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    private val thumb = checkNotNull(AppCompatResources.getDrawable(
        this.context, me.zhanghai.android.fastscroll.R.drawable.afs_md2_thumb,
    ))
    private val track = checkNotNull(AppCompatResources.getDrawable(
        this.context, me.zhanghai.android.fastscroll.R.drawable.afs_md2_track,
    ))
    private val scroller = FastScrollerBuilder(this)
        .useMd2Style()
        .setViewHelper(adapter)
        .setThumbDrawable(thumb)
        .setTrackDrawable(track)
        .build()
    private var intercepted = false
    private var tint = 0

    init { addView(content, LayoutParams(-1, -1)) }

    fun setContent(block: @Composable () -> Unit) = content.setContent(content = block)
    fun disposeContent() = content.disposeComposition()

    fun updateColor(primary: Int, onSurface: Int) {
        if (tint == primary) return
        tint = primary
        thumb.setTint(primary)
        track.setTint(onSurface)
        invalidate()
    }

    fun updateBounds(bounds: Rect) {
        if (adapter.bounds == bounds) return
        adapter.bounds = bounds
        scroller.setPadding(0, adapter.bounds.top.toInt(), 0, (height - adapter.bounds.bottom.toInt()).coerceAtLeast(0))
        invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) intercepted = false
        val inList = event.y >= adapter.bounds.top && event.y <= adapter.bounds.bottom
        val handled = (inList || intercepted) && adapter.touch?.test(event) == true
        if (handled && !intercepted) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        val consumed = intercepted || handled
        intercepted = handled
        return if (consumed) true else super.dispatchTouchEvent(event)
    }
}
