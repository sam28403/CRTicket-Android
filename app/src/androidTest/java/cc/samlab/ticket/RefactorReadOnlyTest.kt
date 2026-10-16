package cc.samlab.ticket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalNotifications
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.web.LocalAssetClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 不申请权限、不发布通知、不保存文件，也不写入用户车票或个人设置。 */
@RunWith(AndroidJUnit4::class)
class RefactorReadOnlyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun bridgeAndNotificationRoutesPreserveUserRecordsAndPreferences() {
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            await { evaluate(activity,
                "Boolean(window.crticketNavigate && document.querySelector('#app')?.__vue_app__)") == "true" }
            val dataBefore = records(activity)
            val preferencesBefore = context.getSharedPreferences("android", 0).all.toMap()
            assertEquals("\"phone\"", evaluate(activity,
                "JSON.parse(window.CRTicketAndroid.configuration()).backend"))
            assertEquals("true", evaluate(activity, """
                [
                    'stationArrivalState', 'enableStationArrival', 'disableStationArrival',
                    'requestTrainTrackingPermission', 'trainTrackingState',
                    'startTrainTracking', 'stopTrainTracking',
                    'requestNotificationPermission', 'notifyTicketAvailability',
                    'configuration', 'systemDarkMode', 'setAppearance',
                    'request', 'cancel', 'save', 'external'
                ].every(name => typeof window.CRTicketAndroid[name] === 'function')
            """.trimIndent()))
            open(activity, Intent().putExtra(ArrivalNotifications.OPEN_STATION, "香港西九龙"))
            await { evaluate(activity, "decodeURIComponent(location.hash)") ==
                "\"#/station?station=香港西九龙\"" }
            await {
                var visible = false
                instrumentation.runOnMainSync {
                    visible = activity.findViewById<android.view.View>(R.id.expressive_station_view)
                        ?.visibility == android.view.View.VISIBLE
                }
                visible
            }
            open(activity, Intent().putExtra(MonitorNotifications.OPEN_MONITOR, true))
            await { evaluate(activity, "location.hash") == "\"#/monitor\"" }
            open(activity, Intent().putExtra(TrainTrackingNotifications.OPEN_TRACKING, "/train"))
            await { evaluate(activity, "location.hash") == "\"#/train\"" }
            await { evaluate(activity,
                "typeof window.crticketNotificationResult === 'function'") == "true" }

            assertEquals(dataBefore, records(activity))
            assertEquals(preferencesBefore, context.getSharedPreferences("android", 0).all)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "Bridge, station/monitor/train routes: PASS; user data unchanged\n")
            })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun localAssetClientPreservesOriginRestrictionsAndResponseHeaders() = withWebView { view ->
        val client = LocalAssetClient(context.assets, {}, {}, {})
        for (url in listOf(
            "http://appassets.androidplatform.net/index.html",
            "https://example.com/index.html",
            "https://appassets.androidplatform.net:444/index.html",
            "https://appassets.androidplatform.net/../index.html",
        )) {
            assertEquals(403, requireNotNull(client.shouldInterceptRequest(view,
                Request(url))).statusCode)
        }
        val response = requireNotNull(client.shouldInterceptRequest(view,
            Request("${MainActivity.ORIGIN}/index.html")))
        assertEquals(200, response.statusCode)
        assertEquals("text/html", response.mimeType)
        assertEquals("no-cache", response.responseHeaders["Cache-Control"])
        assertEquals("nosniff", response.responseHeaders["X-Content-Type-Options"])
        assertTrue(response.responseHeaders["Content-Security-Policy"]!!.contains("frame-src 'none'"))
        response.data.close()
    }

    private fun records(activity: MainActivity) = evaluate(activity, """
        JSON.stringify([
            localStorage.getItem('crticket-device-tickets-v1'),
            localStorage.getItem('crticket-device-owner-v1'),
            localStorage.getItem('crticket-user'),
            localStorage.getItem('crticket-theme')
        ])
    """.trimIndent())

    private fun open(activity: MainActivity, intent: Intent) {
        instrumentation.runOnMainSync { instrumentation.callActivityOnNewIntent(activity, intent) }
    }

    private fun evaluate(activity: MainActivity, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                result = it
                done.countDown()
            }
        }
        assertTrue("WebView callback timeout", done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        fail("Read-only UI check timed out")
    }

    private fun withWebView(check: (WebView) -> Unit) {
        lateinit var view: WebView
        instrumentation.runOnMainSync { view = WebView(context) }
        try {
            check(view)
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    private class Request(url: String) : WebResourceRequest {
        private val uri = Uri.parse(url)
        override fun getUrl() = uri
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }
}
