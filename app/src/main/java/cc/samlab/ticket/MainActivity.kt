package cc.samlab.ticket

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import cc.samlab.ticket.ui.AppearanceController
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
        private const val FILE_REQUEST = 10
        private const val SAVE_REQUEST = 11
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }

    lateinit var webView: WebView
        private set
    private val worker = Executors.newFixedThreadPool(4)
    private val phoneHttp by lazy {
        cc.samlab.ticket.network.PhoneHttp(worker, ::callback)
    }
    private val preferences by lazy { getSharedPreferences("android", MODE_PRIVATE) }
    private val notifications by lazy { MonitorNotifications(this, ::callback) }
    private val trainTracking by lazy { TrainTrackingBridge(this, ::callback) }
    private var fileChooser: ValueCallback<Array<Uri>>? = null
    private var pendingSave: File? = null
    private var pendingSaveId: String? = null
    private var saving = false
    private lateinit var appearance: AppearanceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView = WebView(this)
        if (preferences.getString("assetVersion", "") != BuildConfig.VERSION_NAME) {
            webView.clearCache(true)
            preferences.edit()
                .putString("assetVersion", BuildConfig.VERSION_NAME)
                .apply()
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        container.addView(webView, LinearLayout.LayoutParams(-1, -1))
        container.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val padding = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(container)
        appearance = AppearanceController(this, container, webView)
        appearance.apply(appearance.isSystemDark())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true // System document picker URIs, never arbitrary file:// URLs.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = false
            if (Build.VERSION.SDK_INT >= 33) {
                isAlgorithmicDarkeningAllowed = false
            }
            userAgentString += " CRTicketAndroid/2.0"
        }
        webView.addJavascriptInterface(AndroidBridge(), "CRTicketAndroid")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                appearance.updateSystem(resources.configuration)
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                if (request.url.scheme != "https" || request.url.host != "appassets.androidplatform.net" || request.url.port != -1 && request.url.port != 443) {
                    return errorResponse(403, "Forbidden")
                }
                val path = request.url.path.orEmpty().removePrefix("/").ifEmpty { "index.html" }
                if (path.split('/').any { it == "." || it == ".." } || path.contains('\\')) return errorResponse(403, "Forbidden")
                return try {
                    val stream = assets.open("web/$path")
                    WebResourceResponse(mimeType(path), "UTF-8", 200, "OK", mapOf(
                        "Cache-Control" to if (path.endsWith(".html")) {
                            "no-cache"
                        } else {
                            "public, max-age=31536000, immutable"
                        },
                        "X-Content-Type-Options" to "nosniff",
                        "Content-Security-Policy" to "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self' blob: data:; frame-src 'none'; object-src 'none'; base-uri 'self'"
                    ), stream)
                } catch (_: Exception) { errorResponse(404, "Not Found") }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && (uri.port == -1 || uri.port == 443)) return false
                if (request.isForMainFrame && (uri.scheme == "https" || uri.scheme == "http")) openExternal(uri)
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) toast("页面加载失败，可在系统返回后重新打开应用")
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileChooser?.onReceiveValue(null)
                fileChooser = callback
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    val accepted = params.acceptTypes.filter { it.contains('/') && !it.contains(';') }.toTypedArray()
                    if (accepted.isNotEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, accepted)
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    startActivityForResult(intent, FILE_REQUEST)
                } catch (_: Exception) { fileChooser?.onReceiveValue(null); fileChooser = null; toast("无法打开系统文件选择器") }
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message).setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        pendingSave = savedInstanceState?.getString("saveFile")?.let { name -> File(cacheDir, name).takeIf { it.exists() && it.parentFile == cacheDir } }
        pendingSaveId = savedInstanceState?.getString("saveId")
        saving = pendingSave != null
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            val trackingRoute = intent.getStringExtra(TrainTrackingNotifications.OPEN_TRACKING)
            val route = if (trackingRoute != null) {
                "#$trackingRoute"
            } else if (intent.getBooleanExtra(MonitorNotifications.OPEN_MONITOR, false)) {
                "#/monitor"
            } else {
                ""
            }
            webView.loadUrl("$ORIGIN/index.html$route")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MonitorNotifications.OPEN_MONITOR, false)) {
            webView.evaluateJavascript("window.location.hash = '/monitor'", null)
        }
        intent.getStringExtra(TrainTrackingNotifications.OPEN_TRACKING)?.let { route ->
            webView.evaluateJavascript("window.location.hash = ${JSONObject.quote(route)}", null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notifications.onRequestPermissionsResult(requestCode, grantResults)
        trainTracking.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun errorResponse(status: Int, reason: String) = WebResourceResponse("text/plain", "UTF-8", status, reason, emptyMap(), ByteArrayInputStream(reason.toByteArray()))

    private fun mimeType(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json", "geojson" -> "application/json"
        "ttf" -> "font/ttf"
        "woff2" -> "font/woff2"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun navigateBack() {
        webView.evaluateJavascript("Boolean(window.crticketBack && window.crticketBack())") { handled ->
            if (handled != "true") {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        }
    }

    @Deprecated("Legacy Android back button")
    // API 33+ uses the OnBackInvokedCallback registered in onCreate; retain this only for API 26–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() { navigateBack() }

    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { toast("未找到可打开链接的浏览器") }
    }

    private fun toast(message: String) { runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() } }

    private fun callback(function: String, id: String, result: JSONObject) {
        runOnUiThread {
            if (!isDestroyed) webView.evaluateJavascript("window.$function?.(${JSONObject.quote(id)},$result)", null)
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface fun requestTrainTrackingPermission(id: String) {
            runOnUiThread { trainTracking.requestPermission(id) }
        }

        @JavascriptInterface fun trainTrackingState(id: String) {
            runOnUiThread { trainTracking.state(id) }
        }

        @JavascriptInterface fun startTrainTracking(id: String, payload: String) {
            runOnUiThread { trainTracking.start(id, payload) }
        }

        @JavascriptInterface fun stopTrainTracking(id: String) {
            runOnUiThread { trainTracking.stop(id) }
        }

        @JavascriptInterface fun requestNotificationPermission(id: String) {
            runOnUiThread { notifications.requestPermission(id) }
        }

        @JavascriptInterface fun notifyTicketAvailability(id: String, title: String, body: String) {
            runOnUiThread { notifications.publish(id, title, body) }
        }

        @JavascriptInterface fun systemDarkMode(): Boolean {
            return appearance.isSystemDark()
        }

        @JavascriptInterface fun setAppearance(dark: Boolean) {
            runOnUiThread { appearance.apply(dark) }
        }
        @JavascriptInterface fun configuration(): String {
            return JSONObject().put("backend", "phone").toString()
        }
        @JavascriptInterface fun external(url: String) { val uri = Uri.parse(url); if (uri.scheme == "http" || uri.scheme == "https") runOnUiThread { openExternal(uri) } }
        @JavascriptInterface fun cancel(id: String) { phoneHttp.cancel(id) }

        @JavascriptInterface fun request(id: String, method: String, path: String, body: String) {
            phoneHttp.enqueue(id, method, path)
        }

        @JavascriptInterface fun save(id: String, filename: String, mime: String, data: String) {
            runOnUiThread {
                if (saving) {
                    callback("crticketSaveResult", id, JSONObject().put("success", false).put("message", "请先完成当前文件保存"))
                    return@runOnUiThread
                }
                saving = true
                worker.execute {
                    try {
                        require(data.length <= MAX_FILE_BYTES * 4 / 3 + 4) { "文件不能超过20MB" }
                        val bytes = Base64.decode(data, Base64.DEFAULT)
                        require(bytes.size <= MAX_FILE_BYTES) { "文件不能超过20MB" }
                        val file = File(cacheDir, "export-${UUID.randomUUID()}.tmp").apply { writeBytes(bytes) }
                        runOnUiThread {
                            pendingSave = file; pendingSaveId = id
                            try {
                                val safeName = filename.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}]"), "").take(180).ifEmpty { "CRTicket-export" }
                                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(mime.substringBefore(';').ifEmpty { "application/octet-stream" }).putExtra(Intent.EXTRA_TITLE, safeName), SAVE_REQUEST)
                            } catch (error: Exception) { finishSave(false, error.message ?: "无法打开文件保存器") }
                        }
                    } catch (error: Exception) {
                        runOnUiThread { saving = false; callback("crticketSaveResult", id, JSONObject().put("success", false).put("message", error.message)) }
                    }
                }
            }
        }
    }

    private fun finishSave(success: Boolean, message: String, cancelled: Boolean = false) {
        pendingSave?.delete(); pendingSave = null; saving = false
        pendingSaveId?.let { callback("crticketSaveResult", it, JSONObject().put("success", success).put("cancelled", cancelled).put("message", message)) }
        pendingSaveId = null
        if (!cancelled) toast(message)
    }

    @Deprecated("System file picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        notifications.onActivityResult(requestCode)
        trainTracking.onActivityResult(requestCode)
        when (requestCode) {
            FILE_REQUEST -> {
                val result = if (resultCode == RESULT_OK) data?.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
                    ?: data?.data?.let { arrayOf(it) } else null
                fileChooser?.onReceiveValue(result); fileChooser = null
            }
            SAVE_REQUEST -> {
                val destination = data?.data
                if (resultCode != RESULT_OK || destination == null) { finishSave(false, "已取消保存", true); return }
                val file = pendingSave ?: run { finishSave(false, "待保存文件已失效"); return }
                worker.execute {
                    try {
                        contentResolver.openOutputStream(destination, "wt").use { output ->
                            requireNotNull(output) { "无法写入所选位置" }
                            file.inputStream().use { it.copyTo(output) }
                        }
                        runOnUiThread { finishSave(true, "文件已保存") }
                    } catch (error: Exception) { runOnUiThread { finishSave(false, error.message ?: "文件保存失败") } }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString("saveFile", pendingSave?.name)
        outState.putString("saveId", pendingSaveId)
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() {
        super.onResume()
        webView.onResume()
        appearance.updateSystem(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appearance.updateSystem(newConfig)
    }
    override fun onDestroy() {
        fileChooser?.onReceiveValue(null)
        phoneHttp.close()
        worker.shutdownNow()
        webView.removeJavascriptInterface("CRTicketAndroid"); webView.destroy()
        super.onDestroy()
    }
}
