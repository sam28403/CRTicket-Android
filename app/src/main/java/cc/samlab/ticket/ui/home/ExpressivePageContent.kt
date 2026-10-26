package cc.samlab.ticket.ui.home

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.history.*
import cc.samlab.ticket.ui.generator.*
import cc.samlab.ticket.ui.monitor.*
import cc.samlab.ticket.ui.navigation.ExpressiveRoutes
import cc.samlab.ticket.ui.settings.*
import cc.samlab.ticket.ui.station.*
import cc.samlab.ticket.ui.statistics.*
import cc.samlab.ticket.ui.trainlist.*
import cc.samlab.ticket.ui.trainquery.*
import org.json.JSONObject

/** Screen rendering is independent of navigation and never changes host View visibility. */
internal class ExpressivePageContent(
    activity: ComponentActivity,
    private val settings: NativeSettingsController,
    private val trainList: NativeTrainListController,
    private val history: NativeHistoryController,
    private val station: NativeStationBoardController,
    private val trainQuery: NativeTrainQueryController,
    private val monitor: NativeTicketMonitorController,
    private val generator: NativeGeneratorController,
    private val web: WebView,
    private val navigation: HomeNavigationState,
    private val onNavigate: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onExternal: (String) -> Unit,
) {
    val statistics = StatisticsUiState()
    val dataVersions = AboutDataVersions(activity)
    val search = cc.samlab.ticket.ui.search.NativeSearchController(activity, web)
    private val statisticsBody = StatisticsBodyLayout(activity)
    private val backend = web.parent as FrameLayout

    @Composable
    fun Render(location: String, active: Boolean, embedded: Boolean, dark: Boolean, pureBlack: Boolean) {
        when (ExpressiveRoutes.path(location)) {
            "/price" -> cc.samlab.ticket.ui.fare.FareCalculatorScreen(
                dark, pureBlack, embedded, onBack, onNavigate,
            )
            "/price/list" -> cc.samlab.ticket.ui.fare.FareTableScreen(dark, pureBlack, onBack)
            "/sale-time" -> cc.samlab.ticket.ui.sale.SaleTimeScreen(
                station, dark, pureBlack, embedded, active, onBack, onNavigate,
            )
            "/sale-time/reminders" -> cc.samlab.ticket.ui.sale.SaleRemindersScreen(
                dark, pureBlack, active, onBack,
            )
            "/railfan/speedometer", "/railfan/speedometer/guide",
            "/railfan/speedometer/live", "/railfan/speedometer/track" ->
                cc.samlab.ticket.ui.speedometer.SpeedometerScreen(
                    location, dark, pureBlack, embedded, active, onBack, onNavigate,
                )
            "/railfan/lines" -> cc.samlab.ticket.ui.railfan.LineQueryScreen(
                dark, pureBlack, embedded, active, onBack,
            )
            "/railfan/routing" -> cc.samlab.ticket.ui.railfan.RoutingQueryScreen(
                trainList.routing, dark, pureBlack, embedded, active, onBack, onNavigate,
            )
            "/railfan/vehicle" -> cc.samlab.ticket.ui.railfan.VehicleQueryScreen(
                trainList, dark, pureBlack, embedded, active, onBack, onNavigate,
                initialTrain = android.net.Uri.parse(location).getQueryParameter("train").orEmpty(),
                initialVehicle = android.net.Uri.parse(location).getQueryParameter("vehicle").orEmpty(),
            )
            "/railfan/visits" -> cc.samlab.ticket.ui.railfan.VisitedStationsScreen(
                station, dark, pureBlack, embedded, active, onBack, onNavigate,
            )
            in railfanDestinations.map { it.route } -> {
                val destination = railfanDestinations.first {
                    it.route == ExpressiveRoutes.path(location)
                }
                cc.samlab.ticket.ui.railfan.RailfanPlaceholderScreen(
                    destination.title, dark, pureBlack, embedded, onBack,
                )
            }
            "/search" -> cc.samlab.ticket.ui.search.ExpressiveSearchScreen(
                search, settings, dark, pureBlack, active, onBack, onNavigate, trainList,
            )
            "/" -> ExpressiveGeneratorScreen(generator, dark, pureBlack, onBack, embedded, active)
            "/home" -> if (embedded) {
                ExpressiveGeneratorScreen(generator, dark, pureBlack, onBack, true, active)
            }
                else ExpressiveHomeScreen(dark, active, onNavigate, pureBlack, navigation)
            "/help", "/help/tickets", "/help/query",
            "/help/backup", "/help/arrival", "/help/monitor",
            "/help/sale", "/help/speed", "/help/vehicle",
            "/help/general" ->
                cc.samlab.ticket.ui.help.ExpressiveHelpScreen(
                    location, dark, pureBlack, embedded, active, onBack, onNavigate,
                )
            "/about/credits" -> ExpressiveCreditsScreen(dark, pureBlack, onBack, onExternal)
            "/about" -> About(dark, pureBlack, embedded)
            "/about/report" -> cc.samlab.ticket.ui.report.ExpressiveReportScreen(
                dark, pureBlack, onBack, onExternal,
            )
            "/settings", "/settings/arrival", "/settings/permissions", "/settings/map", "/settings/language", "/settings/sale-reminders" -> {
                val page = when (ExpressiveRoutes.path(location)) {
                    "/settings/sale-reminders" -> SettingsPage.SALE_REMINDERS
                    "/settings/arrival" -> SettingsPage.ARRIVAL
                    "/settings/permissions" -> SettingsPage.PERMISSIONS
                    "/settings/map" -> SettingsPage.MAP
                    "/settings/language" -> SettingsPage.LANGUAGE
                    else -> SettingsPage.MAIN
                }
                ExpressiveSettingsScreen(dark, settings, onBack, pureBlack, active, embedded, page) {
                    onNavigate("/sale-time/reminders")
                }
            }
            "/monitor", "/monitor/settings" -> ExpressiveTicketMonitorScreen(
                monitor, dark, pureBlack, location == "/monitor/settings", onBack,
                { onNavigate("/monitor/settings") }, embedded, active,
            )
            "/history", "/history/editor" -> {
                val draft = remember { history.ui.draft.takeIf { location == "/history/editor" } }
                ExpressiveHistoryScreen(history, dark, pureBlack, onBack, embedded, active, draft)
                BackHandler(enabled = active && draft != null && history.ui.busy) { }
            }
            "/list" -> TrainList(dark, pureBlack, embedded)
            "/station" -> ExpressiveStationBoardScreen(
                station.stateFor(location), dark, pureBlack, onBack, onNavigate, active, embedded,
            )
            "/train" -> ExpressiveTrainQueryScreen(
                trainQuery, dark, pureBlack, onBack, onNavigate, embedded, active,
                location,
            )
            "/user" -> {
                ExpressiveStatisticsScreen(
                    statisticsBody, statistics, dark, pureBlack, onBack, embedded, active,
                ) { month ->
                    statistics.dismissMonth()
                    web.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('crticket-statistics-month'," +
                            "{detail:${JSONObject.quote(month)}}))", null,
                    )
                }
                SideEffect { moveWebTo(statisticsBody) }
                DisposableEffect(Unit) {
                    onDispose { if (web.parent === statisticsBody) moveWebTo(backend) }
                }
            }
            else -> {
                AndroidView(
                    factory = { FrameLayout(it).apply { moveWebTo(this) } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { parent -> if (web.parent === parent) moveWebTo(backend) },
                )
                // Web dialogs retain their existing close-before-leave behavior.
                BackHandler(enabled = active) { onBack() }
            }
        }
    }

    @Composable
    private fun About(dark: Boolean, pureBlack: Boolean, embedded: Boolean) {
        ExpressiveAboutScreen(
            dark, onBack, onExternal, pureBlack, embedded, dataVersions,
            onCredits = { onNavigate("/about/credits") },
        ) {
            onNavigate("/about/report")
        }
    }

    @Composable
    private fun TrainList(dark: Boolean, pureBlack: Boolean, embedded: Boolean) {
        val list = rememberLazyListState()
        var lastRevision by rememberSaveable { mutableIntStateOf(trainList.revision) }
        LaunchedEffect(trainList.revision) {
            if (lastRevision != trainList.revision) {
                list.scrollToItem(0)
                lastRevision = trainList.revision
            }
        }
        AndroidView(
            factory = { TrainListScrollView(it).apply { id = R.id.expressive_train_list_view } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.disposeContent() },
            update = { scroll ->
                scroll.setContent {
                    ExpressiveTrainListScreen(
                        trainList, dark, pureBlack, onBack, onNavigate, scroll, embedded, list,
                    )
                }
            },
        )
    }

    private fun moveWebTo(parent: FrameLayout) {
        if (web.parent === parent) return
        (web.parent as? ViewGroup)?.removeView(web)
        parent.addView(web, FrameLayout.LayoutParams(-1, -1))
    }

    fun close() {
        search.close()
        dataVersions.close()
        trainList.close()
        history.close()
        station.close()
        trainQuery.close()
    }
}
