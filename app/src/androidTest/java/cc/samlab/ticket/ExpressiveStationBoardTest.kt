package cc.samlab.ticket

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpressiveStationBoardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
        evaluate("window.crticketNavigate('/station')")
        compose.waitUntil(20_000) {
            runCatching {
                compose.onAllNodesWithTag("station-screen").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @After fun close() { instrumentation.runOnMainSync { activity.finish() } }

    @Test fun liveQueryTabsFollowMapAndOfflineNavigation() {
        compose.onNode(hasText("车站大屏") and hasAnyAncestor(hasTestTag("station-screen")))
            .assertIsDisplayed()
        compose.onNodeWithTag("station-tab-all").assertDoesNotExist()
        compose.onNodeWithTag("station-query").performTextInput("beijingnan")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("北京南").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("北京南", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("station-submit").performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("station-tab-all").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("定位到当前时间").assertIsDisplayed()
        compose.onNodeWithText("跟随当前时间").assertIsDisplayed()
        compose.onNodeWithText("绿色：即将到发").assertDoesNotExist()
        assertEquals("true", evaluate("document.querySelector('.station-page') === null"))
        capture("station-live")
        compose.onNodeWithText("始发站").assertDoesNotExist()
        compose.onNodeWithText("终到站").assertDoesNotExist()
        compose.onNodeWithTag("station-board").performTouchInput { swipeUp() }
        compose.onNodeWithTag("station-follow").assertIsOff()
        compose.onNodeWithText("定位到当前时间").performClick()
        compose.onNodeWithTag("station-follow").assertIsOn()
        compose.onNodeWithTag("station-tab-departure").performClick()
        compose.onNodeWithText("始发站").assertDoesNotExist()
        capture("station-departure")
        compose.onNodeWithTag("station-tab-arrival").performClick()
        compose.onNodeWithText("终到站").assertDoesNotExist()
        compose.onNodeWithTag("station-tab-map").performClick()
        compose.onNodeWithTag("station-board").assertDoesNotExist()
        compose.waitUntil(20_000) {
            mapView()?.let { evaluate("Boolean(document.querySelector('.leaflet-overlay-pane path'))", it) == "true" }
                ?: false
        }
        compose.onNodeWithTag("station-tab-map").assertIsDisplayed()
        compose.waitUntil(30_000) {
            mapView()?.let { evaluate("Boolean(document.querySelector('.leaflet-tile-loaded'))", it) == "true" }
                ?: false
        }
        compose.waitForIdle()
        capture("station-map")
        compose.onNodeWithTag("station-tab-all").performClick()
        compose.waitForIdle()
        val list = compose.onNodeWithTag("station-board")
        assertTrue(list.onChildren().fetchSemanticsNodes().size < 30)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val train = compose.onAllNodes(
            hasTestTag("station-train-code") and
                hasAnyAncestor(hasTestTag("station-board")) and
                SemanticsMatcher("fully visible train code") {
                    it.positionInRoot.y >= viewport.top &&
                        it.positionInRoot.y + it.size.height <= viewport.bottom
                },
            useUnmergedTree = true,
        ).onFirst()
        train.assertIsDisplayed().performClick()
        Thread.sleep(200)
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "Train code tap destination: ${evaluate("location.hash")}\n")
        })
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        assertEquals("true", evaluate("location.hash.includes('source=llt')"))
    }

    @Test fun invalidQueryAndEmptyResultCanBeRetried() {
        compose.onNodeWithTag("station-submit").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("请输入车站名称（不超过 30 个字）").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("station-query").performTextInput("不存在的车站")
        compose.onNodeWithTag("station-submit").performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("共 0 趟").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("本站当天没有该类型的车次").assertIsDisplayed()
        capture("station-empty")
    }

    @Test fun trainAndStationBackRestoreTheTabAndExactListOffset() {
        evaluate("window.crticketNavigate('/station?station=' + encodeURIComponent('北京南'))")
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("station-tab-departure").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("station-tab-departure").performClick()
        compose.onNodeWithTag("station-follow").performClick()
        val list = compose.onNodeWithTag("station-board")
        list.performScrollToIndex(25)
        list.performTouchInput { swipeUp(durationMillis = 250) }
        compose.waitForIdle()
        val position = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 25f)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val cardTag = list.onChildren().fetchSemanticsNodes().first {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("station-card-") &&
                it.positionInRoot.y >= viewport.top &&
                it.positionInRoot.y + it.size.height <= viewport.bottom
        }.config[SemanticsProperties.TestTag]
        val card = compose.onNodeWithTag(cardTag)
        card.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 8f, height / 2f)) }
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        pressBack()
        awaitStation("北京南")
        compose.onNodeWithTag("station-tab-departure").assertIsSelected()
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.001f)

        compose.onNode(
            hasTestTag("station-train-code") and hasAnyAncestor(hasTestTag(cardTag)),
            useUnmergedTree = true,
        ).performClick()
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        pressBack()
        awaitStation("北京南")
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.001f)
        val station = compose.onNode(
            hasTestTag("station-name") and hasAnyAncestor(hasTestTag(cardTag)),
            useUnmergedTree = true,
        )
        val destination = station.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertNotEquals("北京南", destination)
        station.performClick()
        awaitStation(destination)
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("station-tab-all").fetchSemanticsNodes().isNotEmpty()
        }
        val probe = StationSurfaceProbe(activity.findViewById(R.id.expressive_navigation_view))
        probe.start()
        try {
            pressBack()
            awaitStation("北京南")
            Thread.sleep(400)
        } finally { probe.stop() }
        assertTrue("No display frames observed", probe.frames >= 5)
        assertEquals("Station surface faded during back navigation", 1f, probe.minimumAlpha, 0.001f)
        assertEquals("Station surface was hidden during back navigation", 0, probe.hiddenFrames)
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "Station back: ${probe.frames} frames, alpha=${probe.minimumAlpha}, hidden=${probe.hiddenFrames}\n")
        })
        awaitStation("北京南")
        compose.onNodeWithTag("station-tab-departure").assertIsSelected()
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.001f)
        capture("station-restored")
    }

    private fun awaitStation(name: String) {
        compose.waitUntil(20_000) {
            var visible = false
            instrumentation.runOnMainSync {
                val view = activity.findViewById<android.view.View>(R.id.expressive_navigation_view)
                visible = view.visibility == android.view.View.VISIBLE && view.alpha == 1f && view.translationX == 0f
            }
            visible && evaluate("new URLSearchParams(location.hash.split('?')[1]).get('station')") == org.json.JSONObject.quote(name) &&
                runCatching {
                    compose.onNodeWithTag("station-query").fetchSemanticsNode()
                        .config[SemanticsProperties.EditableText].text == name &&
                        compose.onAllNodesWithTag("station-board").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        // WebView compositor frames run outside Compose's test clock.
        Thread.sleep(1000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "station-verification")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun mapView(): android.webkit.WebView? {
        var result: android.webkit.WebView? = null
        fun find(view: android.view.View) {
            if (view is android.webkit.WebView && view.tag?.toString()?.contains("/station-map?") == true) result = view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))
        }
        instrumentation.runOnMainSync { find(activity.window.decorView) }
        return result
    }

    private fun evaluate(script: String, view: android.webkit.WebView = activity.webView): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { result = it; latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return result
    }
}
