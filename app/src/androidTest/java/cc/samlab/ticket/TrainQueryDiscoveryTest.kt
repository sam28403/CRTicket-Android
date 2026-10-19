package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TrainQueryDiscoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun historyRecommendationsAndOnlineNavigation() {
        MonitorLifecycleDeviceFixture(instrumentation).shell("input keyevent KEYCODE_WAKEUP")
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        var savedHistory: String? = null
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            compose.waitUntil(30_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
            savedHistory = evaluate("localStorage.getItem('crticket-train-query-history')")
            evaluate("localStorage.setItem('crticket-train-query-history', JSON.stringify(['G1','D2','K3']))")
            val apiFile = instrumentation.targetContext.assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && instrumentation.targetContext.assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            evaluate("""
                window.discoveryReady = false;
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    window.discoveryRequests = [];
                    api.defaults.adapter = async config => {
                        window.discoveryRequests.push(config.url);
                        const trains = Array.from({length: 12}, (_, i) => ({
                            train: 'G' + (i + 1), from: '北京南', to: '上海虹桥',
                            duration: '04:30', stationCount: 5, mileage: 1318
                        }));
                        const data = config.url === '/llt-trains'
                            ? {success: true, trains, date: config.params.date}
                            : {success: true, train: config.params.train, date: config.params.date,
                               from: '北京南', to: '上海虹桥', stops: [
                                 {no: '01', train: config.params.train, station: '北京南',
                                  departure: '08:00', mileage: 0, arrivalDay: 0},
                                 {no: '02', train: config.params.train, station: '上海虹桥',
                                  arrival: '12:30', mileage: 1318, arrivalDay: 0}
                               ]};
                        return {status: 200, statusText: 'OK', headers: {}, config, data};
                    };
                    window.discoveryReady = true;
                });
            """.trimIndent())
            compose.waitUntil(10_000) { evaluate("window.discoveryReady") == "true" }
            compose.waitForIdle()
            evaluate("window.crticketNavigate('/train')")
            try {
                compose.waitUntil(30_000) {
                    runCatching {
                        compose.onAllNodesWithText("今日随机推荐").fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                }
            } catch (failure: Throwable) {
                throw AssertionError(
                    "Discovery not shown: route=${evaluate("location.hash")}; " +
                        runCatching {
                            compose.onNodeWithTag("expressive-navigation").printToString()
                        }.getOrDefault("No native hierarchy"),
                    failure,
                )
            }
            compose.onNodeWithTag("train-query-history").assertIsDisplayed()
            compose.onNodeWithText("D2").assertIsDisplayed()
            compose.onNodeWithTag("train-query-content")
                .performScrollToNode(hasText("北京南"))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("北京南").fetchSemanticsNodes().isNotEmpty()
            }
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "train-query-discovery.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
            val titleBefore = compose.onNodeWithTag("train-query-recommendation-title")
                .fetchSemanticsNode().boundsInWindow.top
            compose.onNodeWithTag("train-query-clear-history").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("train-query-history").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("train-query-clear-history").assertDoesNotExist()
            val titleAfter = compose.onNodeWithTag("train-query-recommendation-title")
                .fetchSemanticsNode().boundsInWindow.top
            assertTrue("Recommendations should fill the cleared history space", titleAfter < titleBefore)
            assertEquals("null", evaluate("localStorage.getItem('crticket-train-query-history')"))
            compose.onAllNodes(hasClickAction() and hasText("北京南", substring = true)).onFirst().performClick()
            compose.waitUntil(15_000) {
                evaluate("window.discoveryRequests.includes('/train-info')") == "true"
            }
            assertEquals("true", evaluate("!new URLSearchParams(location.hash.split('?')[1]).has('source')"))
            assertEquals("true", evaluate("new URLSearchParams(location.hash.split('?')[1]).has('date')"))
            assertEquals("true", evaluate("JSON.parse(localStorage.getItem('crticket-train-query-history')).length <= 10"))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
            }
            instrumentation.runOnMainSync {
                val dispatcher = activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(
                    0f, 300f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT,
                ))
                dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(
                    100f, 300f, 0.4f, androidx.activity.BackEventCompat.EDGE_LEFT,
                ))
            }
            compose.waitUntil(10_000) {
                compose.onAllNodes(
                    hasTestTag("train-query-recommendation-title") and
                        hasAnyAncestor(hasTestTag("navigation-page:/train")),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            instrumentation.runOnMainSync {
                activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            repeat(3) {
                pressBack()
                compose.waitUntil(10_000) {
                    runCatching {
                        compose.onNodeWithTag("train-query-clear-history").assertIsDisplayed()
                        compose.onNodeWithTag("train-query-recommendation-title").assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }
                compose.onAllNodes(hasClickAction() and hasText("北京南", substring = true))
                    .onFirst().performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
                }
            }
        } finally {
            if (savedHistory != null) {
                evaluate("""
                    const saved = $savedHistory;
                    if (saved === null) localStorage.removeItem('crticket-train-query-history');
                    else localStorage.setItem('crticket-train-query-history', saved);
                """.trimIndent())
            }
            instrumentation.runOnMainSync { activity.finish() }
        }
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
}
