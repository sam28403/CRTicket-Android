package cc.samlab.ticket

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.speedometer.SpeedometerStore
import cc.samlab.ticket.speedometer.SpeedPoint
import cc.samlab.ticket.speedometer.SpeedRecording
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapSettingsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var welcome: WelcomeTestState

    @Before fun acceptCurrentBuildForMapTests() {
        welcome = WelcomeTestState(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After fun restoreWelcomeState() = welcome.close()

    @Test fun historyTrackFollowsMapPreferenceAndKeepsRecordedPointsLocal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val source = preferences.getString("mapSource", null)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true)
            .putString("mapSource", "france").commit()
        val fixture = TabletPageFixture(compose)
        instrumentation.runOnMainSync { SpeedometerStore.initialize(context) }
        val history = SpeedometerStore.history
        val record = SpeedRecording(
            "map-source-test", "G1", "北京", "北京南", 0,
            elapsed = 120_000, meters = 1000.0,
            points = listOf(
                SpeedPoint(39.9, 116.4, 0, 0),
                SpeedPoint(39.9009, 116.4, 36_000, 0),
                SpeedPoint(39.9009, 116.4012, 37_000, 0),
                SpeedPoint(39.9018, 116.4012, 38_350, 0),
                SpeedPoint(39.9018, 116.4, 41_050, 0),
            ),
        )
        try {
            instrumentation.runOnMainSync { SpeedometerStore.history = listOf(record) }
            fixture.evaluate("window.crticketNavigate('/railfan/speedometer/track?id=${record.id}')")
            for (selected in listOf("france", "hot", "transport")) {
                instrumentation.runOnMainSync {
                    preferences.edit().putString("mapSource", selected).commit()
                }
                compose.waitUntil(20_000) {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready = webViews(fixture.activity.window.decorView).any {
                            it.url?.contains("source=$selected&track=true") == true
                        }
                    }
                    ready
                }
                compose.waitUntil(20_000) {
                    val latch = CountDownLatch(1)
                    var result = ""
                    instrumentation.runOnMainSync {
                        webViews(fixture.activity.window.decorView).first {
                            it.url?.contains("source=$selected&track=true") == true
                        }.evaluateJavascript(
                            "Boolean(document.querySelector('.leaflet-overlay-pane canvas')) && " +
                                "window.crticketTrackSegments?.[0]?.length === 5 && " +
                                "!location.href.includes('39.9') && " +
                                "document.querySelector('[role=dialog]') === null",
                        ) { result = it; latch.countDown() }
                    }
                    latch.await(5, TimeUnit.SECONDS) && result == "true"
                }
            }
            assertTrue(SpeedometerStore.history.first().points == record.points)
            fixture.capture("history-map-source")
        } finally {
            instrumentation.runOnMainSync { SpeedometerStore.history = history }
            fixture.close()
            preferences.edit().putString("mapSource", source)
                .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
        }
    }

    @Test fun mapPickerPersistsSourceAndWarnsWhenPreviewFails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val source = preferences.getString("mapSource", null)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true)
            .putString("mapSource", "hot").commit()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_map)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("settings-map-preview").assertIsDisplayed()
            compose.onNodeWithTag("map-source:hot").assertIsSelected()
            compose.onNodeWithTag("map-source:osm").performScrollTo().performClick()
            compose.onNodeWithTag("map-source:osm").assertIsSelected()
            assertEquals("osm", preferences.getString("mapSource", null))
            pressBack()
            compose.onNode(hasText(context.getString(R.string.settings_map)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("map-source:osm").assertIsSelected()
            compose.onNodeWithTag("settings-map-preview").performScrollTo()
            compose.waitUntil(20_000) {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = webViews(fixture.activity.window.decorView).any {
                        it != fixture.activity.webView && it.url?.contains("source=osm") == true
                    }
                }
                ready
            }
            instrumentation.runOnMainSync {
                webViews(fixture.activity.window.decorView).first {
                    it != fixture.activity.webView && it.url?.contains("source=osm") == true
                }.evaluateJavascript("console.error('CRTicketMap:failed')", null)
            }
            compose.onNodeWithText(context.getString(R.string.map_source_failed_title))
                .assertIsDisplayed()
            fixture.capture("map-preview-failure")
            compose.onNodeWithText(context.getString(R.string.map_source_dismiss)).performClick()
            compose.onNodeWithTag("map-source:hot").performScrollTo().performClick()
            compose.onNodeWithTag("settings-map-preview").performScrollTo()
            fixture.capture("map-settings-beijing")
        } finally {
            fixture.close()
            preferences.edit().putString("mapSource", source)
                .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
        }
    }

    private fun webViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
