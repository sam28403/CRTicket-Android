package cc.samlab.ticket

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import cc.samlab.ticket.arrival.ArrivalNotifications
import cc.samlab.ticket.arrival.StationArrivalBridge
import cc.samlab.ticket.files.DocumentFiles
import cc.samlab.ticket.monitor.TicketMonitorBridge
import cc.samlab.ticket.network.PhoneHttp
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.ui.AppearanceController
import cc.samlab.ticket.ui.ExpressiveDialogHost
import cc.samlab.ticket.ui.ExpressiveRefreshHost
import cc.samlab.ticket.ui.home.ExpressiveHomeHost
import cc.samlab.ticket.ui.navigation.ExpressiveRoutes
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.window.WindowLayoutObserver
import cc.samlab.ticket.web.AndroidBridge
import cc.samlab.ticket.web.LocalAssetClient
import cc.samlab.ticket.web.WebViewLayout
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        const val ORIGIN = LocalAssetClient.ORIGIN
    }

    lateinit var webView: WebView
        private set
    private val worker = Executors.newFixedThreadPool(4)
    private val phoneHttp by lazy { PhoneHttp(worker, ::callback) }
    private val preferences by lazy { getSharedPreferences("android", MODE_PRIVATE) }
    private val notifications by lazy { MonitorNotifications(this, ::callback) }
    private val trainTracking by lazy { TrainTrackingBridge(this, ::callback) }
    private val ticketMonitor by lazy { TicketMonitorBridge(this, ::callback) }
    private val dialogs by lazy { ExpressiveDialogHost(this) }
    private val refresh by lazy { ExpressiveRefreshHost(this, webView) }
    private val home by lazy { ExpressiveHomeHost(this) }
    private val settings by lazy { NativeSettingsController(this, webView, worker) }
    private val trainList by lazy {
        cc.samlab.ticket.ui.trainlist.NativeTrainListController(this, webView, worker)
    }
    private val monitorPage by lazy {
        cc.samlab.ticket.ui.monitor.NativeTicketMonitorController(this, webView)
    }
    private val trainQuery by lazy {
        cc.samlab.ticket.ui.trainquery.NativeTrainQueryController(this, webView)
    }
    private val stationBoard by lazy {
        cc.samlab.ticket.ui.station.NativeStationBoardController(this, webView, worker)
    }
    private val stationArrival by lazy { StationArrivalBridge(this, ::callback, dialogs) }
    private val history by lazy {
        cc.samlab.ticket.ui.history.NativeHistoryController(this, webView, worker)
    }
    private val files by lazy { DocumentFiles(this, worker, ::callback, ::toast) }
    private val generator by lazy {
        cc.samlab.ticket.ui.generator.NativeGeneratorController(this, webView)
    }
    private lateinit var appearance: AppearanceController
    private var navigationReady = false
    private var pendingRoute: String? = null
    private var pendingNotification = false
    private var pendingNativeRoute: String? = null
    private val navigationBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = navigateBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView = WebView(this)
        if (preferences.getString("assetVersion", "") != BuildConfig.VERSION_NAME) {
            webView.clearCache(true)
            preferences.edit().putString("assetVersion", BuildConfig.VERSION_NAME).apply()
        }
        val container = WebViewLayout.create(this, webView)
        setContentView(container)
        refresh.attach(container)
        settings.ui.restore(savedInstanceState)
        history.restore(savedInstanceState)
        home.attach(
            container, ::navigateTo, ::navigateBack, { openExternal(Uri.parse(it)) },
            settings, trainList, history, stationBoard, trainQuery, monitorPage, generator, webView,
            ::onNativeRoute,
        )
        WindowLayoutObserver(this) { home.updateFolds(it) }
        val route = initialNotificationRoute()
        pendingRoute = if (route == "#/home") savedInstanceState?.getString("pendingRoute") else null
        pendingNotification = savedInstanceState?.getBoolean("pendingNotification") ?: false
        if (route == "#/home") {
            home.setPage(savedInstanceState?.getString("nativePage") ?: "/home")
        } else {
            home.openNotification(route.removePrefix("#"))
        }
        refresh.enabled = !home.usesWebContent
        webView.visibility = if (home.visible && !home.usesWebContent) android.view.View.INVISIBLE else android.view.View.VISIBLE
        appearance = AppearanceController(this, container, webView, dialogs, refresh, home)
        appearance.apply(home.dark)
        configureWebView()
        navigationBack.isEnabled = !home.visible
        onBackPressedDispatcher.addCallback(this, navigationBack)
        files.restore(savedInstanceState)
        if (savedInstanceState == null || route != "#/home" ||
            webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl("$ORIGIN/index.html$route")
        }
        clearNotificationRoute()
    }

    private fun configureWebView() {
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            // 系统文档选择器使用 content URI，不允许任意 file URI。
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = false
            if (Build.VERSION.SDK_INT >= 33) isAlgorithmicDarkeningAllowed = false
            userAgentString += " CRTicketAndroid/2.0"
        }
        webView.addJavascriptInterface(
            AndroidBridge(
                this, phoneHttp, appearance, notifications, trainTracking, stationArrival,
                files, ::openExternal,
                refresh,
                ticketMonitor,
                ::receiveWebPage,
                { result -> settings.receive(result) },
                trainList::receive,
                history::receive,
                history::receiveOperation,
                generator::receive,
                generator::savedDraft,
                stationBoard::receive,
                monitorPage::receive,
                trainQuery::receive,
                home::updateStatisticsState,
                home::openStatisticsMonthPicker,
                home::setBackTarget,
            ),
            "CRTicketAndroid",
        )
        webView.webViewClient = LocalAssetClient(
            assets,
            {
                appearance.onPageReady(resources.configuration)
                refresh.finish()
            },
            ::openExternal,
            ::toast,
            appearance::onDocumentStarted,
        )
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean = files.choose(callback, params)

            override fun onJsAlert(
                view: WebView,
                url: String,
                message: String,
                result: JsResult,
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(R.string.action_confirm) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }
    }

    private fun initialNotificationRoute(): String {
        val station = intent.getStringExtra(ArrivalNotifications.OPEN_STATION)
        val tracking = intent.getStringExtra(TrainTrackingNotifications.OPEN_TRACKING)
        return when {
            station != null -> "#${stationRoute(station)}"
            tracking != null -> "#$tracking"
            intent.getBooleanExtra(MonitorNotifications.OPEN_MONITOR, false) -> "#/monitor"
            else -> "#/home"
        }
    }

    private fun clearNotificationRoute() {
        intent.removeExtra(ArrivalNotifications.OPEN_STATION)
        intent.removeExtra(TrainTrackingNotifications.OPEN_TRACKING)
        intent.removeExtra(MonitorNotifications.OPEN_MONITOR)
    }

    private fun receiveWebPage(page: String, replace: Boolean) {
        val expected = pendingNativeRoute
        if (expected != null && page != expected && !ExpressiveRoutes.addsDefaultDate(expected, page)) return
        pendingNativeRoute = null
        navigationReady = true
        home.setPage(page, replace)
        updateNavigationUi()
        pendingRoute?.let { route ->
            pendingRoute = null
            val notification = pendingNotification
            pendingNotification = false
            navigateTo(route, notification)
        }
    }

    private fun updateNavigationUi() {
        refresh.enabled = home.route != "/user"
        if (home.route == "/settings") settings.refresh()
        navigationBack.isEnabled = !home.visible
        webView.visibility = if (home.visible && !home.usesWebContent) {
            android.view.View.INVISIBLE
        } else android.view.View.VISIBLE
        if (::appearance.isInitialized) appearance.apply(home.dark)
        if (!home.visible) webView.requestFocus()
    }

    private fun stationRoute(station: String) = "/station?station=${Uri.encode(station)}"

    private fun navigateTo(route: String) = navigateTo(route, notification = false)

    private fun navigateTo(route: String, notification: Boolean) {
        // The native home can receive a tap before the WebView router finishes starting.
        if (!navigationReady) {
            pendingRoute = route
            pendingNotification = notification
            return
        }
        if (notification) home.openNotification(route) else home.navigate(route)
    }

    private fun onNativeRoute(route: String, back: Boolean, mirror: Boolean) {
        updateNavigationUi()
        if (mirror && navigationReady) {
            val webRoute = ExpressiveRoutes.webLocation(route)
            pendingNativeRoute = webRoute
            webView.evaluateJavascript(
                "window.crticketSyncNativeRoute?.(${JSONObject.quote(webRoute)},$back)", null,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ArrivalNotifications.OPEN_STATION)?.let { station ->
            navigateTo(stationRoute(station))
            intent.removeExtra(ArrivalNotifications.OPEN_STATION)
        }
        if (intent.getBooleanExtra(MonitorNotifications.OPEN_MONITOR, false)) navigateTo("/monitor")
        intent.getStringExtra(TrainTrackingNotifications.OPEN_TRACKING)?.let {
            navigateTo(it, notification = true)
        }
        clearNotificationRoute()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notifications.onRequestPermissionsResult(requestCode, grantResults)
        trainTracking.onRequestPermissionsResult(requestCode, grantResults)
        stationArrival.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun navigateBack() {
        if (home.visible) {
            if (!home.pop()) {
                if (home.isHome || home.exitsAtRoot) finish() else home.navigate("/home")
            }
        } else {
            completeBackNavigation()
        }
    }

    private fun completeBackNavigation() {
        if (home.isHome) {
            finish()
            return
        }
        webView.evaluateJavascript("Boolean(window.crticketBack && window.crticketBack())") { handled ->
            if (handled != "true") {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            toast(this.getString(R.string.browser_unavailable))
        }
    }

    private fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun callback(function: String, id: String, result: JSONObject) {
        runOnUiThread {
            if (!isDestroyed) {
                webView.evaluateJavascript("window.$function?.(${JSONObject.quote(id)},$result)", null)
            }
        }
    }

    @Deprecated("System file picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        notifications.onActivityResult(requestCode)
        trainTracking.onActivityResult(requestCode)
        stationArrival.onActivityResult(requestCode)
        files.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString("nativePage", home.location)
        outState.putString("pendingRoute", pendingRoute)
        outState.putBoolean("pendingNotification", pendingNotification)
        settings.ui.save(outState)
        history.save(outState)
        files.saveState(outState)
    }

    override fun onPause() {
        stationArrival.onPause()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        appearance.updateSystem(resources.configuration)
        stationArrival.onResume()
        if (home.route == "/settings") settings.refresh()
        trainTracking.restore()
        ticketMonitor.restore()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appearance.updateSystem(newConfig)
    }

    override fun onDestroy() {
        home.close()
        stationArrival.close()
        refresh.close()
        files.closeChooser()
        phoneHttp.close()
        worker.shutdownNow()
        webView.removeJavascriptInterface("CRTicketAndroid")
        webView.destroy()
        super.onDestroy()
    }
}
