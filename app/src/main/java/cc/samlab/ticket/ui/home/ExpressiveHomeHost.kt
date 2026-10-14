package cc.samlab.ticket.ui.home

import android.view.View
import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.settings.ExpressiveSettingsScreen

/** Native home content; the WebView router owns navigation and restoration. */
class ExpressiveHomeHost(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("android", Context.MODE_PRIVATE)
    var dark by mutableStateOf(preferences.getBoolean("appearanceDark",
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES))
    var pureBlack by mutableStateOf(preferences.getBoolean("appearancePureBlack", false))
    var route by mutableStateOf("")
        private set
    val visible: Boolean
        get() = route.isNotEmpty()
    val isHome: Boolean
        get() = route == "/home"
    private lateinit var homeView: ComposeView
    private lateinit var aboutView: ComposeView
    private lateinit var settingsView: ComposeView
    private val motion = ExpressivePageMotion()
    private var previewHome = false

    val backgroundColor: Int
        get() = homeColorScheme(activity, dark, pureBlack).surfaceContainer.toArgb()

    fun attach(
        container: FrameLayout,
        onNavigate: (String) -> Unit,
        onBack: () -> Unit,
        onExternal: (String) -> Unit,
        settings: NativeSettingsController,
    ) {
        homeView = ComposeView(activity).apply {
            id = R.id.expressive_home_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(if (isHome) Modifier else Modifier.clearAndSetSemantics {}) {
                    ExpressiveHomeScreen(dark, isHome, onNavigate, pureBlack)
                }
            }
        }
        aboutView = ComposeView(activity).apply {
            id = R.id.expressive_about_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(if (route == "/about") Modifier else Modifier.clearAndSetSemantics {}) {
                    ExpressiveAboutScreen(dark, onBack, onExternal, pureBlack)
                }
            }
        }
        settingsView = ComposeView(activity).apply {
            id = R.id.expressive_settings_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(if (route == "/settings") Modifier else Modifier.clearAndSetSemantics {}) {
                    ExpressiveSettingsScreen(dark, settings, onBack, pureBlack, route == "/settings")
                }
            }
        }
        container.addView(homeView, FrameLayout.LayoutParams(-1, -1))
        container.addView(aboutView, FrameLayout.LayoutParams(-1, -1))
        container.addView(settingsView, FrameLayout.LayoutParams(-1, -1))
    }

    fun setPage(page: String) {
        val next = if (page in setOf("/home", "/about", "/settings")) page else ""
        if (next == route) return
        val previous = route
        val completedGesture = motion.returning
        motion.finish()
        route = next
        if (!isHome) previewHome = previous == "/home"
        updateVisibility()
        if (previous != route && visible && !completedGesture) {
            motion.enter(
                view = currentView(),
                destination = homeView.takeIf { !isHome && previewHome },
                reverse = isHome && previous.isNotEmpty(),
                onEnd = ::updateVisibility,
            )
        }
    }

    fun startBack(event: BackEventCompat) {
        if (!visible || isHome) return
        motion.start(currentView(), homeView.takeIf { previewHome }, event)
    }

    fun progressBack(event: BackEventCompat) = motion.progress(event)

    fun cancelBack() = motion.cancel(::updateVisibility)

    fun animateBack(onEnd: () -> Unit): Boolean {
        if (!visible || isHome) return false
        motion.commit(currentView(), homeView.takeIf { previewHome }, onEnd)
        return true
    }

    fun close() = motion.finish()

    private fun currentView() = when (route) {
        "/settings" -> settingsView
        "/about" -> aboutView
        else -> homeView
    }

    private fun updateVisibility() {
        homeView.visibility = if (isHome) View.VISIBLE else View.GONE
        aboutView.visibility = if (route == "/about") View.VISIBLE else View.GONE
        settingsView.visibility = if (route == "/settings") View.VISIBLE else View.GONE
    }
}
