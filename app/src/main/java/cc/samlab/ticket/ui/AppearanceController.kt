package cc.samlab.ticket.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.webkit.WebView
import cc.samlab.ticket.ui.home.ExpressiveHomeHost
import cc.samlab.ticket.ui.home.homeColorScheme
import androidx.compose.ui.graphics.toArgb

class AppearanceController(
    private val activity: Activity,
    private val root: View,
    private val webView: WebView,
    private val dialogs: ExpressiveDialogHost,
    private val refresh: ExpressiveRefreshHost,
    private val home: ExpressiveHomeHost,
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
        apply(home.dark)
        // Let WebView finish applying the native configuration before notifying its page.
        webView.removeCallbacks(notifySystem)
        webView.postDelayed(notifySystem, 100)
    }

    fun apply(dark: Boolean, pureBlack: Boolean = home.pureBlack) {
        activity.getSharedPreferences("android", Activity.MODE_PRIVATE).edit()
            .putBoolean("appearanceDark", dark).putBoolean("appearancePureBlack", pureBlack).apply()
        dialogs.dark = dark
        dialogs.pureBlack = pureBlack
        refresh.dark = dark
        refresh.pureBlack = pureBlack
        home.dark = dark
        home.pureBlack = pureBlack
        val colors = homeColorScheme(activity, dark, pureBlack)
        val color = if (dark && pureBlack) Color.BLACK else colors.surfaceContainer.toArgb()
        root.setBackgroundColor(if (home.visible) home.backgroundColor else color)
        webView.setBackgroundColor(color)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('crticket-native-palette'," +
                "{detail:${nativeThemePalette(colors)}}))",
            null,
        )
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
