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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import cc.samlab.ticket.arrival.ArrivalNotifications
import cc.samlab.ticket.arrival.StationArrivalBridge
import cc.samlab.ticket.files.DocumentFiles
import cc.samlab.ticket.network.PhoneHttp
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.ui.AppearanceController
import cc.samlab.ticket.ui.ExpressiveDialogHost
import cc.samlab.ticket.ui.ExpressiveRefreshHost
import cc.samlab.ticket.web.AndroidBridge
import cc.samlab.ticket.web.LocalAssetClient
import cc.samlab.ticket.web.WebViewLayout
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
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
    private val dialogs by lazy { ExpressiveDialogHost(this) }
    private val refresh by lazy { ExpressiveRefreshHost(this, webView) }
    private val stationArrival by lazy { StationArrivalBridge(this, ::callback, dialogs) }
    private val files by lazy { DocumentFiles(this, worker, ::callback, ::toast) }
    private lateinit var appearance: AppearanceController

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
        appearance = AppearanceController(this, container, webView, dialogs, refresh)
        appearance.apply(appearance.isSystemDark())
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        files.restore(savedInstanceState)
        if (savedInstanceState == null || intent.hasExtra(ArrivalNotifications.OPEN_STATION) ||
            webView.restoreState(savedInstanceState) == null) {
            val route = initialNotificationRoute()
            webView.loadUrl("$ORIGIN/index.html$route")
            intent.removeExtra(ArrivalNotifications.OPEN_STATION)
        }
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
            ),
            "CRTicketAndroid",
        )
        webView.webViewClient = LocalAssetClient(
            assets,
            {
                appearance.updateSystem(resources.configuration)
                refresh.finish()
            },
            ::openExternal,
            ::toast,
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
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
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
            else -> ""
        }
    }

    private fun stationRoute(station: String) = "/station?station=${Uri.encode(station)}"

    private fun navigateTo(route: String) {
        webView.evaluateJavascript("window.location.hash = ${JSONObject.quote(route)}", null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ArrivalNotifications.OPEN_STATION)?.let { station ->
            navigateTo(stationRoute(station))
            intent.removeExtra(ArrivalNotifications.OPEN_STATION)
        }
        if (intent.getBooleanExtra(MonitorNotifications.OPEN_MONITOR, false)) navigateTo("/monitor")
        intent.getStringExtra(TrainTrackingNotifications.OPEN_TRACKING)?.let(::navigateTo)
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
            toast("未找到可打开链接的浏览器")
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
        files.saveState(outState)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        appearance.updateSystem(resources.configuration)
        stationArrival.onResume()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appearance.updateSystem(newConfig)
    }

    override fun onDestroy() {
        refresh.close()
        files.closeChooser()
        phoneHttp.close()
        worker.shutdownNow()
        webView.removeJavascriptInterface("CRTicketAndroid")
        webView.destroy()
        super.onDestroy()
    }
}
