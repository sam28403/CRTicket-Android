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
import androidx.compose.material3.ColorScheme

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
    private var savedAppearance: Pair<Boolean, Boolean>? = null
    private var webPalette: ColorScheme? = null
    private var systemBarDark: Boolean? = null
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

    fun onDocumentStarted() {
        // Fragment navigation also finishes a page, but only a new document loses its palette.
        webPalette = null
    }

    fun onPageReady(configuration: Configuration) {
        updateSystem(configuration)
    }

    fun updateSystem(configuration: Configuration) {
        systemDark = isNight(configuration)
        val preferences = activity.getSharedPreferences("android", Activity.MODE_PRIVATE)
        apply(ThemePreference.resolveDark(preferences.getString("themePreference", null), systemDark))
        // Let WebView finish applying the native configuration before notifying its page.
        webView.removeCallbacks(notifySystem)
        webView.postDelayed(notifySystem, 100)
    }

    fun setThemePreference(preference: String) {
        if (!preference.matches(Regex("^(classic|contrast)-(system|light|dark)$"))) return
        activity.getSharedPreferences("android", Activity.MODE_PRIVATE).edit()
            .putString("themePreference", preference)
            .apply()
        apply(
            ThemePreference.resolveDark(preference, systemDark),
            preference.startsWith("contrast-"),
        )
    }

    fun apply(dark: Boolean, pureBlack: Boolean = home.pureBlack) {
        val appearance = dark to pureBlack
        if (savedAppearance != appearance) {
            activity.getSharedPreferences("android", Activity.MODE_PRIVATE).edit()
                .putBoolean("appearanceDark", dark).putBoolean("appearancePureBlack", pureBlack).apply()
            savedAppearance = appearance
        }
        dialogs.dark = dark
        dialogs.pureBlack = pureBlack
        refresh.dark = dark
        refresh.pureBlack = pureBlack
        home.dark = dark
        home.pureBlack = pureBlack
        val colors = homeColorScheme(activity, dark, pureBlack)
        val liveSpeedometer = home.route == "/railfan/speedometer/live"
        val color = if (liveSpeedometer || dark && pureBlack) Color.BLACK else colors.surfaceContainer.toArgb()
        root.setBackgroundColor(color)
        webView.setBackgroundColor(color)
        if (webPalette !== colors) {
            webPalette = colors
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('crticket-native-palette'," +
                    "{detail:${nativeThemePalette(colors)}}))",
                null,
            )
        }
        val barsDark = dark || liveSpeedometer
        if (systemBarDark == barsDark) return
        systemBarDark = barsDark
        if (Build.VERSION.SDK_INT >= 30) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(
                if (barsDark) 0 else flags,
                flags
            )
        } else {
            @Suppress("DEPRECATION")
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            val current = activity.window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = if (barsDark) {
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
