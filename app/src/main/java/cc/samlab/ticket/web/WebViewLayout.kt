package cc.samlab.ticket.web

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout

object WebViewLayout {
    fun create(context: Context, webView: WebView): FrameLayout {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            addView(webView, FrameLayout.LayoutParams(-1, -1))
        }
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val padding = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime(),
                )
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        return container
    }
}
