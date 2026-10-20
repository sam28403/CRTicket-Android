package cc.samlab.ticket.web

import android.app.Activity
import android.net.Uri
import android.webkit.JavascriptInterface
import cc.samlab.ticket.arrival.StationArrivalBridge
import cc.samlab.ticket.files.DocumentFiles
import cc.samlab.ticket.monitor.TicketMonitorBridge
import cc.samlab.ticket.network.PhoneHttp
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.ui.AppearanceController
import cc.samlab.ticket.ui.ExpressiveRefreshHost
import cc.samlab.ticket.ui.settings.settingsTextResources
import org.json.JSONObject

class AndroidBridge(
    private val activity: Activity,
    private val phoneHttp: PhoneHttp,
    private val appearance: AppearanceController,
    private val notifications: MonitorNotifications,
    private val trainTracking: TrainTrackingBridge,
    private val stationArrival: StationArrivalBridge,
    private val files: DocumentFiles,
    private val openExternal: (Uri) -> Unit,
    private val refresh: ExpressiveRefreshHost,
    private val ticketMonitor: TicketMonitorBridge,
    private val showNativePage: (String, Boolean) -> Unit,
    private val receiveSettings: (JSONObject) -> Unit,
    private val receiveTrainList: (String) -> Unit,
    private val receiveHistory: (String) -> Unit,
    private val receiveHistoryOperation: (String) -> Unit,
    private val receiveGenerator: (String) -> Unit,
    private val readGeneratorDraft: () -> String,
    private val receiveStationBoard: (String) -> Unit,
    private val receiveTicketMonitorPage: (String) -> Unit,
    private val receiveTrainQuery: (String) -> Unit,
    private val receiveStatisticsState: (Boolean, Boolean) -> Unit,
    private val chooseStatisticsMonth: (String) -> Unit,
    private val updateBackTarget: (String) -> Unit,
    private val receiveSearch: (String) -> Unit,
) {
    private fun onUi(action: () -> Unit) = activity.runOnUiThread(action)

    @JavascriptInterface
    fun setHomeVisible(visible: Boolean) = onUi { showNativePage(if (visible) "/home" else "", false) }

    @JavascriptInterface
    fun setNativePage(route: String) = onUi { showNativePage(route, false) }

    @JavascriptInterface
    fun setNativeRoute(route: String, replace: Boolean) = onUi { showNativePage(route, replace) }

    @JavascriptInterface
    fun setNativeBackTarget(route: String) = onUi { updateBackTarget(route) }

    @JavascriptInterface
    fun supportsStatisticsPage(): Boolean = true

    @JavascriptInterface
    fun statisticsState(empty: Boolean, loading: Boolean) = onUi {
        receiveStatisticsState(empty, loading)
    }

    @JavascriptInterface
    fun chooseStatisticsMonth(month: String) = onUi {
        chooseStatisticsMonth.invoke(month)
    }

    @JavascriptInterface
    fun trainListResult(payload: String) = receiveTrainList(payload)

    @JavascriptInterface
    fun searchResult(payload: String) = receiveSearch(payload)

    @JavascriptInterface
    fun stationBoardResult(payload: String) = receiveStationBoard(payload)

    @JavascriptInterface
    fun ticketMonitorPageResult(payload: String) = receiveTicketMonitorPage(payload)

    @JavascriptInterface
    fun resetTicketMonitor(id: String) = onUi { ticketMonitor.reset(id) }

    @JavascriptInterface
    fun refreshTicketMonitor(id: String) = onUi { ticketMonitor.refresh(id) }

    @JavascriptInterface
    fun trainQueryResult(payload: String) = receiveTrainQuery(payload)

    @JavascriptInterface
    fun trainQueryToast(message: String) = onUi {
        android.widget.Toast.makeText(activity, message.take(500), android.widget.Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun historyResult(payload: String) = receiveHistory(payload)

    @JavascriptInterface
    fun generatorResult(payload: String) = receiveGenerator(payload)

    @JavascriptInterface
    fun generatorDraft(): String = readGeneratorDraft()

    @JavascriptInterface
    fun historyOperationResult(payload: String) {
        if (payload.length <= 64_000) receiveHistoryOperation(payload)
    }

    @JavascriptInterface
    fun settingsStrings(): String = settingsTextResources(activity)

    @JavascriptInterface
    fun settingsResult(payload: String) {
        if (payload.length > 500_000) return
        val result = runCatching { JSONObject(payload) }.getOrNull() ?: return
        onUi { receiveSettings(result) }
    }

    @JavascriptInterface
    fun setPullRefreshProgress(progress: Double) = onUi { refresh.update(progress.toFloat()) }

    @JavascriptInterface
    fun refreshPage() = onUi { refresh.refresh() }

    @JavascriptInterface
    fun stationArrivalState(id: String) = onUi { stationArrival.state(id) }

    @JavascriptInterface
    fun enableStationArrival(id: String) = onUi { stationArrival.enable(id) }

    @JavascriptInterface
    fun disableStationArrival(id: String) = onUi { stationArrival.disable(id) }

    @JavascriptInterface
    fun requestTrainTrackingPermission(id: String) = onUi { trainTracking.requestPermission(id) }

    @JavascriptInterface
    fun trainTrackingState(id: String) = onUi { trainTracking.state(id) }

    @JavascriptInterface
    fun startTrainTracking(id: String, payload: String) = onUi { trainTracking.start(id, payload) }

    @JavascriptInterface
    fun stopTrainTracking(id: String) = onUi { trainTracking.stop(id) }

    @JavascriptInterface
    fun ticketMonitorStatus(id: String) = onUi { ticketMonitor.state(id) }

    @JavascriptInterface
    fun startTicketMonitor(id: String, payload: String) =
        onUi { ticketMonitor.start(id, payload) }

    @JavascriptInterface
    fun updateTicketMonitor(id: String, payload: String) =
        onUi { ticketMonitor.update(id, payload) }

    @JavascriptInterface
    fun stopTicketMonitor(id: String) = onUi { ticketMonitor.stop(id) }

    @JavascriptInterface
    fun requestNotificationPermission(id: String) = onUi { notifications.requestPermission(id) }

    @JavascriptInterface
    fun notifyTicketAvailability(id: String, title: String, body: String) =
        onUi { notifications.publish(id, title, body) }

    @JavascriptInterface
    fun systemDarkMode(): Boolean = appearance.isSystemDark()

    @JavascriptInterface
    fun setThemePreference(preference: String) = onUi { appearance.setThemePreference(preference) }

    @JavascriptInterface
    fun setAppearance(dark: Boolean) = onUi { appearance.apply(dark) }

    @JavascriptInterface
    fun setThemeAppearance(dark: Boolean, pureBlack: Boolean) = onUi { appearance.apply(dark, pureBlack) }

    @JavascriptInterface
    fun configuration(): String = JSONObject().put("backend", "phone").toString()

    @JavascriptInterface
    fun external(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme in setOf("http", "https")) onUi { openExternal(uri) }
    }

    @JavascriptInterface
    fun cancel(id: String) = phoneHttp.cancel(id)

    @JavascriptInterface
    fun request(id: String, method: String, path: String, body: String) {
        phoneHttp.enqueue(id, method, path)
    }

    @JavascriptInterface
    fun save(id: String, filename: String, mime: String, data: String) =
        onUi { files.save(id, filename, mime, data) }
}
