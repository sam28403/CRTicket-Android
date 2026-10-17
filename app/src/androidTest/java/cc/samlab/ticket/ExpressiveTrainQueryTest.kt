package cc.samlab.ticket

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpressiveTrainQueryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun nativeQueryFallbackStopsDateAndMap() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
            evaluate("window.crticketNavigate('/train')")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("train-query-code").fetchSemanticsNodes().isNotEmpty()
            }
            val apiFile = instrumentation.targetContext.assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && instrumentation.targetContext.assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            evaluate("""
                window.queryFixtureReady = false;
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    window.queryFixtureRequests = [];
                    api.defaults.adapter = async config => {
                        window.queryFixtureRequests.push(config.url);
                        if (config.url === '/train-info') throw new Error('online unavailable');
                        if (config.url === '/station') return {
                            status: 200, statusText: 'OK', headers: {}, config, data: {
                                success: true, station: config.params.station,
                                date: config.params.date, source: '路路通', trains: []
                            }
                        };
                        return { status: 200, statusText: 'OK', headers: {}, config, data: window.queryDynamicFixture || {
                            success: true, train: 'G1', date: config.params.date,
                            from: '北京南', to: '上海虹桥', source: '路路通', stops: [
                                { no: '01', train: 'G1', station: '北京南', arrival: null,
                                  departure: '23:00', stay: null, mileage: 0, arrivalDay: 0 },
                                { no: '02', train: 'G1', station: '南京南', arrival: '03:00',
                                  departure: '03:05', stay: 5, mileage: 1023, arrivalDay: 1 },
                                { no: '03', train: 'G1', station: '上海虹桥', arrival: '05:00',
                                  departure: null, stay: null, mileage: 1318, arrivalDay: 1 }
                            ]
                        }};
                    };
                    window.queryFixtureReady = true;
                }).catch(error => { window.queryFixtureError = String(error); });
            """.trimIndent())
            compose.waitUntil(10_000) { evaluate("Boolean(window.queryFixtureReady || window.queryFixtureError)") == "true" }
            assertEquals(evaluate("window.queryFixtureError"), "true", evaluate("window.queryFixtureReady"))
            compose.onNodeWithTag("train-query-code").performTextInput("G1")
            compose.onNodeWithTag("train-query-submit").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("train-query-tracking").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("[\"/train-info\",\"/llt-trains/detail\"]", evaluate("JSON.stringify(window.queryFixtureRequests)").let {
                org.json.JSONTokener(it).nextValue().toString()
            })
            compose.onNodeWithText("切换为在线查询").assertIsDisplayed()
            assertEquals("true", evaluate("document.querySelector('.train-query-page') === null"))
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("train-query-tracking").assertIsEnabled(); true }.getOrDefault(false)
            }
            val firstCard = compose.onNodeWithTag("train-query-stop-0").fetchSemanticsNode().boundsInWindow
            val secondCard = compose.onNodeWithTag("train-query-stop-1").fetchSemanticsNode().boundsInWindow
            val grid = compose.onNodeWithTag("train-query-content").fetchSemanticsNode().boundsInWindow
            val density = activity.resources.displayMetrics.density
            if (grid.width / density >= 680f) {
                assertEquals(firstCard.top, secondCard.top, 2f)
                assertTrue(secondCard.left > firstCard.right)
            } else assertTrue(secondCard.top >= firstCard.bottom)
            capture("train-query-form")
            compose.onNodeWithTag("train-query-date").performClick()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("train-query-content").performScrollToNode(hasText("1023 km"))
            compose.onNodeWithText("03:00 (+1 天)").assertIsDisplayed()
            compose.onNodeWithText("5 分钟").assertIsDisplayed()
            compose.onNodeWithText("1023 km").assertIsDisplayed()
            capture("train-query-stops")
            compose.onNodeWithTag("train-query-content").performScrollToNode(hasText("连线表示站序，不代表实际铁路走向。"))
            compose.onNodeWithText("连线表示站序，不代表实际铁路走向。").assertIsDisplayed()
            compose.onNodeWithText("报告错误").assertDoesNotExist()
            capture("train-query-map")
            compose.onNodeWithTag("train-query-content").performScrollToNode(hasTestTag("train-query-export"))
            compose.onNodeWithTag("train-query-export").assertIsDisplayed()
            capture("train-query-export")
            verifyNativeRouteIslands(instrumentation.targetContext)
            val fixture = currentTrainQueryFixture()
            evaluate("window.queryDynamicFixture = $fixture")
            compose.onNodeWithTag("train-query-date").performClick()
            compose.onNodeWithText("取消").performClick()
            // Keep the selected query date in range; fixture offsets remain relative to its own base date.
            compose.onNodeWithTag("train-query-submit").performClick()
            val inStation = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "停在站内")
            val upcoming = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "前方到站")
            compose.onNodeWithTag("train-query-content").performScrollToNode(hasTestTag("train-query-summary"))
            compose.waitUntil(10_000) {
                compose.onAllNodes(inStation).fetchSemanticsNodes().isNotEmpty()
            }
            val summary = compose.onNodeWithTag("train-query-summary").fetchSemanticsNode().boundsInWindow
            val tracking = compose.onNodeWithTag("train-query-tracking").fetchSemanticsNode().boundsInWindow
            assertTrue(tracking.center.x > summary.center.x)
            if (activity.resources.configuration.screenWidthDp >= 840) {
                compose.onNodeWithTag("navigation-menu").assertIsDisplayed()
                compose.onNodeWithTag("navigation-detail").assertIsDisplayed()
            }
            compose.onNodeWithTag("train-query-stop-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
            capture("train-query-current-summary")
            compose.onNodeWithTag("train-query-content").performScrollToNode(hasTestTag("train-query-stop-2"))
            compose.onNodeWithTag("train-query-stop-1").assert(inStation)
            compose.onNodeWithTag("train-query-stop-2").assert(upcoming)
            capture("train-query-current-stops")
            compose.onNodeWithTag("train-query-stop-2").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.9f, height * 0.5f)) }
            compose.waitUntil(10_000) {
                evaluate("new URLSearchParams(location.hash.split('?')[1]).get('station')") == JSONObject.quote("上海虹桥")
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var value = ""
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { value = it; latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return value
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(700)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "train-query-verification")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
