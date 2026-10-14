package cc.samlab.ticket.ui

import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

class ExpressiveRefreshHost(private val activity: ComponentActivity, private val webView: WebView) {
    var pureBlack by mutableStateOf(false)
    var dark by mutableStateOf(
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    )
    private var progress by mutableFloatStateOf(0f)
    private var refreshing by mutableStateOf(false)
    private lateinit var overlay: FrameLayout
    private val reload = Runnable { if (!activity.isDestroyed) webView.reload() }
    private val timeout = Runnable { finish() }

    fun attach(container: FrameLayout) {
        overlay = object : FrameLayout(activity) {
            // The visual layer never steals a WebView drag or a tap underneath it.
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
        }.apply {
            tag = "expressive-pull-refresh"
            visibility = View.GONE
            addView(ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { ExpressiveRefreshIndicator(progress, refreshing, dark, pureBlack) }
            })
        }
        container.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ))
    }

    fun update(value: Float) {
        if (refreshing || !value.isFinite()) return
        progress = value.coerceIn(0f, 1.4f)
        overlay.visibility = if (progress > 0f) View.VISIBLE else View.GONE
    }

    fun refresh() {
        if (refreshing || activity.isDestroyed) return
        refreshing = true
        progress = 1f
        overlay.visibility = View.VISIBLE
        webView.postDelayed(reload, 160)
        webView.postDelayed(timeout, 15_000)
    }

    fun finish() {
        if (!refreshing) return
        webView.removeCallbacks(timeout)
        refreshing = false
        progress = 0f
        overlay.visibility = View.GONE
    }

    fun close() {
        webView.removeCallbacks(reload)
        webView.removeCallbacks(timeout)
    }
}
