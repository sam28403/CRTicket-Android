package cc.samlab.ticket.ui.home

import android.view.View
import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.window.layout.FoldingFeature
import cc.samlab.ticket.ui.window.LocalFoldingFeatures
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
    private lateinit var settingsController: NativeSettingsController
    internal var folds by mutableStateOf<List<FoldingFeature>>(emptyList())
        private set
    internal var wide by mutableStateOf(activity.resources.configuration.screenWidthDp >= 840)
        private set
    private var backProgress by mutableStateOf(0f)
    private var backDirection by mutableStateOf(1f)
    private lateinit var container: FrameLayout
    private val motion = ExpressivePageMotion()
    private var previewHome = false
    private val navigation = HomeNavigationState()
    private val dataVersions = AboutDataVersions(activity)

    val backgroundColor: Int
        get() = homeColorScheme(activity, dark, pureBlack).surfaceContainer.toArgb()

    fun attach(
        container: FrameLayout,
        onNavigate: (String) -> Unit,
        onBack: () -> Unit,
        onExternal: (String) -> Unit,
        settings: NativeSettingsController,
    ) {
        this.container = container
        settingsController = settings
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateWindow() }
        homeView = ComposeView(activity).apply {
            id = R.id.expressive_home_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalFoldingFeatures provides folds) {
                    Box(if (isHome || (wide && visible)) Modifier else Modifier.clearAndSetSemantics {}) {
                        if (wide) ExpressiveNavigationShell(
                            route, dark, pureBlack, settings, backProgress, backDirection,
                            navigation, dataVersions, onNavigate, onBack, onExternal,
                        ) else ExpressiveHomeScreen(dark, isHome, onNavigate, pureBlack, navigation)
                    }
                }
            }
        }
        aboutView = ComposeView(activity).apply {
            id = R.id.expressive_about_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalFoldingFeatures provides folds) {
                    Box(if (route == "/about" && !wide) Modifier else Modifier.clearAndSetSemantics {}) {
                        ExpressiveAboutScreen(dark, onBack, onExternal, pureBlack, dataVersions = dataVersions)
                    }
                }
            }
        }
        settingsView = ComposeView(activity).apply {
            id = R.id.expressive_settings_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalFoldingFeatures provides folds) {
                    Box(if (route == "/settings" && !wide) Modifier else Modifier.clearAndSetSemantics {}) {
                        ExpressiveSettingsScreen(dark, settings, onBack, pureBlack, route == "/settings" && !wide)
                    }
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
        if (next != "/settings") settingsController.ui.resetPage()
        if (!isHome) previewHome = previous == "/home"
        updateVisibility()
        backProgress = 0f
        if (previous != route && visible && !completedGesture && !wide) {
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
        if (hasSettingsDetail()) {
            settingsController.ui.backProgress = 0f
            return
        }
        if (wide) {
            backDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            backProgress = 0f
            return
        }
        motion.start(currentView(), homeView.takeIf { previewHome }, event)
    }

    fun progressBack(event: BackEventCompat) {
        if (hasSettingsDetail()) settingsController.ui.backProgress = event.progress
        else if (wide) backProgress = event.progress else motion.progress(event)
    }

    fun cancelBack() {
        if (hasSettingsDetail()) settingsController.ui.backProgress = 0f
        else if (wide) backProgress = 0f else motion.cancel(::updateVisibility)
    }

    fun animateBack(onEnd: () -> Unit): Boolean {
        if (!visible || isHome) return false
        if (hasSettingsDetail()) {
            settingsController.ui.closeDetail()
            return true
        }
        if (wide) {
            backProgress = 0f
            return false
        }
        motion.commit(currentView(), homeView.takeIf { previewHome }, onEnd)
        return true
    }

    fun close() {
        motion.finish()
        dataVersions.close()
    }

    internal fun updateFolds(features: List<FoldingFeature>) {
        folds = features
        if (::container.isInitialized) updateWindow()
    }

    private fun hasSettingsDetail() = route == "/settings" && settingsController.ui.hasDetail

    private fun updateWindow() {
        val width = (container.width - container.paddingLeft - container.paddingRight) /
            activity.resources.displayMetrics.density
        if (width <= 0) return
        val hasVerticalFold = folds.any { it.orientation == FoldingFeature.Orientation.VERTICAL }
        val next = width >= 840 || (hasVerticalFold && width >= 640)
        if (next == wide) return
        motion.finish()
        backProgress = 0f
        wide = next
        updateVisibility()
    }

    private fun currentView() = when (route) {
        "/settings" -> settingsView
        "/about" -> aboutView
        else -> homeView
    }

    private fun updateVisibility() {
        if (route == "/about" || (wide && isHome)) dataVersions.refresh()
        homeView.visibility = if (isHome || (wide && visible)) View.VISIBLE else View.GONE
        aboutView.visibility = if (route == "/about" && !wide) View.VISIBLE else View.GONE
        settingsView.visibility = if (route == "/settings" && !wide) View.VISIBLE else View.GONE
    }
}
