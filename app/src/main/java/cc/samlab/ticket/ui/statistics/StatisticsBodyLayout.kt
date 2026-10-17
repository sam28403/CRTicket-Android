package cc.samlab.ticket.ui.statistics

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.FrameLayout

/** Observe WebView gestures on the UI thread, without a per-frame JavaScript bridge. */
internal class StatisticsBodyLayout(context: Context) : FrameLayout(context) {
    var onScroll: ((delta: Float, atTop: Boolean) -> Unit)? = null
    var onScrollEnd: (() -> Unit)? = null
    private var lastY = 0f
    private var lastX = 0f
    private var startX = 0f
    private var startY = 0f
    private var scrolling = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                lastX = event.rawX
                startX = lastX
                startY = lastY
                scrolling = false
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.rawY - lastY
                val horizontal = event.rawX - lastX
                if (!scrolling && event.pointerCount == 1) {
                    val verticalDistance = kotlin.math.abs(event.rawY - startY)
                    scrolling = verticalDistance > touchSlop &&
                        verticalDistance > kotlin.math.abs(event.rawX - startX)
                }
                if (scrolling && event.pointerCount == 1 && kotlin.math.abs(delta) > kotlin.math.abs(horizontal)) {
                    val web = getChildAt(0) as? WebView
                    onScroll?.invoke(delta, web?.canScrollVertically(-1) == false)
                }
                lastY = event.rawY
                lastX = event.rawX
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scrolling) onScrollEnd?.invoke()
                scrolling = false
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
