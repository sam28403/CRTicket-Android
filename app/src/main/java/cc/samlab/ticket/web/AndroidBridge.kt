package cc.samlab.ticket.web

import android.app.Activity
import android.net.Uri
import android.webkit.JavascriptInterface
import cc.samlab.ticket.arrival.StationArrivalBridge
import cc.samlab.ticket.files.DocumentFiles
import cc.samlab.ticket.network.PhoneHttp
import cc.samlab.ticket.monitor.TicketMonitorBridge
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.ui.AppearanceController
import cc.samlab.ticket.ui.ExpressiveRefreshHost
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
    private val showNativePage: (String) -> Unit,
    private val receiveSettings: (JSONObject) -> Unit,
    private val receiveTrainList: (String) -> Unit,
) {
    private fun onUi(action: () -> Unit) = activity.runOnUiThread(action)

    @JavascriptInterface
    fun setHomeVisible(visible: Boolean) = onUi { showNativePage(if (visible) "/home" else "") }

    @JavascriptInterface
    fun setNativePage(route: String) = onUi { showNativePage(route) }

    @JavascriptInterface
    fun trainListResult(payload: String) = receiveTrainList(payload)

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
