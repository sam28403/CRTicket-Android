package cc.samlab.ticket.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.webkit.WebView

class AppearanceController(
    private val activity: Activity,
    private val root: View,
    private val webView: WebView,
    private val dialogs: ExpressiveDialogHost,
) {
    @Volatile
    private var systemDark = isNight(activity.resources.configuration)
    private val notifySystem = Runnable {
        if (!activity.isDestroyed) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('crticket-system-theme'," +
                    "{detail:{dark:$systemDark}}))",
                null
            )
        }
    }

    fun isSystemDark(): Boolean = systemDark

    fun updateSystem(configuration: Configuration) {
        systemDark = isNight(configuration)
        // Let WebView finish applying the native configuration before notifying its page.
        webView.removeCallbacks(notifySystem)
        webView.postDelayed(notifySystem, 100)
    }

    fun apply(dark: Boolean) {
        dialogs.dark = dark
        val color = if (dark) Color.rgb(10, 10, 10) else Color.WHITE
        root.setBackgroundColor(color)
        webView.setBackgroundColor(color)
        if (Build.VERSION.SDK_INT >= 30) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(
                if (dark) 0 else flags,
                flags
            )
        } else {
            @Suppress("DEPRECATION")
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            val current = activity.window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = if (dark) {
                current and flags.inv()
            } else {
                current or flags
            }
        }
    }

    private fun isNight(configuration: Configuration): Boolean {
        return configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
