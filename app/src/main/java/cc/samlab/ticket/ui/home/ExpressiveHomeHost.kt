package cc.samlab.ticket.ui.home

import android.content.Context
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.window.layout.FoldingFeature
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ThemePreference
import cc.samlab.ticket.ui.history.NativeHistoryController
import cc.samlab.ticket.ui.monitor.NativeTicketMonitorController
import cc.samlab.ticket.ui.navigation.ExpressiveNavigator
import cc.samlab.ticket.ui.navigation.ExpressiveRoutes
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.settings.SettingsPage
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.trainlist.NativeTrainListController
import cc.samlab.ticket.ui.trainquery.NativeTrainQueryController

/** All Expressive destinations share one ComposeView and one Navigation back stack. */
class ExpressiveHomeHost(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("android", Context.MODE_PRIVATE)
    var dark by mutableStateOf(ThemePreference.isDark(activity))
    var pureBlack by mutableStateOf(preferences.getBoolean("appearancePureBlack", false))
    var location by mutableStateOf("/home")
        private set
    val route: String
        get() = when (val path = ExpressiveRoutes.path(location)) {
            "/settings/arrival", "/settings/permissions" -> "/settings"
            "/history/editor" -> "/history"
            else -> path.takeIf { ExpressiveRoutes.isNative(location) }.orEmpty()
        }
    val visible get() = route.isNotEmpty()
    val isHome get() = route == "/home"
    val usesWebContent get() = route == "/user"
    val backgroundColor get() = homeColorScheme(activity, dark, pureBlack).surfaceContainer.toArgb()
    internal var folds by mutableStateOf<List<FoldingFeature>>(emptyList())
        private set
    internal var wide by mutableStateOf(activity.resources.configuration.screenWidthDp >= 840)
        private set
    private val navigator = ExpressiveNavigator()
    private val navigation = HomeNavigationState()
    private lateinit var pages: ExpressivePageContent
    private lateinit var settings: NativeSettingsController
    private lateinit var history: NativeHistoryController
    private lateinit var container: FrameLayout

    fun attach(
        container: FrameLayout,
        onNavigate: (String) -> Unit,
        onBack: () -> Unit,
        onExternal: (String) -> Unit,
        settings: NativeSettingsController,
        trainList: NativeTrainListController,
        history: NativeHistoryController,
        station: NativeStationBoardController,
        trainQuery: NativeTrainQueryController,
        monitor: NativeTicketMonitorController,
        generator: cc.samlab.ticket.ui.generator.NativeGeneratorController,
        webView: WebView,
        onNativeRoute: (String, Boolean, Boolean) -> Unit,
    ) {
        this.container = container
        this.settings = settings
        this.history = history
        pages = ExpressivePageContent(
            activity, settings, trainList, history, station, trainQuery, monitor, generator,
            webView, navigation, onNavigate, onBack, onExternal,
        )
        settings.onMonitorSettings = { onNavigate("/monitor/settings") }
        settings.ui.onPageChanged = { page, back ->
            if (back) navigator.pop()
            else navigator.navigate(when (page) {
                SettingsPage.ARRIVAL -> "/settings/arrival"
                SettingsPage.PERMISSIONS -> "/settings/permissions"
                SettingsPage.MAIN -> "/settings"
            })
        }
        history.ui.onDetailChanged = { opened ->
            if (opened) navigator.navigate("/history/editor")
            else if (location == "/history/editor") navigator.pop()
        }
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateWindow() }
        val root = ComposeView(activity).apply {
            id = R.id.expressive_navigation_view
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExpressiveAppLayout(
                    location, dark, pureBlack, folds, navigation, navigator,
                    onNavigate = onNavigate,
                    onDestination = { destination, back, mirror ->
                        applyLocation(destination)
                        onNativeRoute(destination, back, mirror)
                    },
                ) { destination, active, embedded ->
                    pages.Render(destination, active, embedded, dark, pureBlack)
                }
            }
        }
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    fun setPage(page: String) {
        if (navigator.currentLocation == null) applyLocation(page.ifEmpty { "/" })
        navigator.navigate(page, fromWeb = true)
    }

    fun navigate(location: String) = navigator.navigate(location)
    fun pop() = navigator.pop()

    private fun applyLocation(destination: String) {
        val previous = location
        location = destination
        val page = when (ExpressiveRoutes.path(destination)) {
            "/settings/arrival" -> SettingsPage.ARRIVAL
            "/settings/permissions" -> SettingsPage.PERMISSIONS
            else -> SettingsPage.MAIN
        }
        settings.ui.showDestination(page)
        if (previous == "/history/editor" && destination != previous) history.ui.leaveDetail()
        if (route != "/user") pages.statistics.dismissMonth()
        if (route == "/about" || (wide && isHome)) pages.dataVersions.refresh()
    }

    fun updateStatisticsState(empty: Boolean, loading: Boolean) = pages.statistics.update(empty, loading)
    fun openStatisticsMonthPicker(month: String) {
        if (usesWebContent) pages.statistics.chooseMonth(month.take(7))
    }

    // The native Navigation back stack now supplies the previous destination.
    fun setBackTarget(@Suppress("UNUSED_PARAMETER") target: String) = Unit

    internal fun updateFolds(features: List<FoldingFeature>) {
        folds = features
        if (::container.isInitialized) updateWindow()
    }

    private fun updateWindow() {
        val width = (container.width - container.paddingLeft - container.paddingRight) /
            activity.resources.displayMetrics.density
        if (width <= 0) return
        val verticalFold = folds.any { it.orientation == FoldingFeature.Orientation.VERTICAL }
        wide = width >= 840 || (verticalFold && width >= 640)
    }

    fun close() {
        navigator.detach()
        pages.close()
    }
}
