package cc.samlab.ticket

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RailwayMapDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun openRailwayMapLoadsInIsolatedPreview() = verify("openrailway")

    private fun verify(source: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val original = preferences.getString("mapSource", null)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        WelcomeTestState(context).use {
            preferences.edit().putString("mapSource", source)
                .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
            val fixture = TabletPageFixture(compose)
            try {
                fixture.navigate("/settings/map")
                compose.onNodeWithTag("settings-map-preview").assertIsDisplayed()
                compose.waitUntil(60_000) {
                    val latch = CountDownLatch(1)
                    var ready = false
                    instrumentation.runOnMainSync {
                        val view = views(fixture.activity.window.decorView).firstOrNull {
                            it != fixture.activity.webView && it.url?.contains("source=$source") == true
                        }
                        if (view == null) latch.countDown()
                        else view.evaluateJavascript(
                            "document.querySelector('.railway-vector-map')?.dataset.ready === 'true' && " +
                                "Boolean(document.querySelector('.maplibregl-canvas')) && " +
                                "Number(document.querySelector('.railway-vector-map')?.dataset.features) > 0",
                        ) { ready = it == "true"; latch.countDown() }
                    }
                    latch.await(5, TimeUnit.SECONDS) && ready
                }
                fixture.capture("$source-map-preview")
            } finally {
                fixture.close()
                preferences.edit().putString("mapSource", original)
                    .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
            }
        }
    }

    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
}
