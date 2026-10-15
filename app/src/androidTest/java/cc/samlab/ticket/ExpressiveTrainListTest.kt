package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
class ExpressiveTrainListTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        assertEquals("Pixel 8 Pro", android.os.Build.MODEL)
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
        evaluate("window.crticketNavigate('/list')")
        compose.waitUntil(20_000) {
            evaluate("location.hash === '#/list'") == "true"
        }
        compose.waitUntil(120_000) {
            runCatching { compose.onAllNodesWithTag("train-list-total").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text.startsWith("共 ") }
            } }.getOrDefault(false)
        }
    }

    @After fun close() {
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun liveDataCalendarRefreshAndDetails() {
        capture("live-list")
        compose.onNodeWithText("车次列表").assertIsDisplayed()
        compose.onNodeWithTag("train-list-date").performClick()
        compose.onNodeWithText("确定").assertIsDisplayed()
        capture("date-picker")
        val tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1)
        compose.onNode(
            hasText("${tomorrow.year}年${tomorrow.monthValue}月${tomorrow.dayOfMonth}日", substring = true)
                and hasClickAction(),
        ).performClick()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(tomorrow.toString()).fetchSemanticsNodes().isNotEmpty()
        }
        capture("next-date")
        // Count actual requests without altering the real data source.
        evaluate("""
            window.listRefreshes = 0;
            window.addEventListener('crticket-train-list-request', () => window.listRefreshes++);
        """.trimIndent())
        compose.onNodeWithTag("train-list").performTouchInput { swipeDown(durationMillis = 600) }
        compose.waitUntil(10_000) { evaluate("window.listRefreshes") == "1" }
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("train-list-total").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text.startsWith("共 ") }
            }
        }
        val first = compose.onNodeWithTag("train-list").onChildren().onFirst()
        first.performClick()
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        pressBack()
        awaitListReady()
        compose.onNodeWithTag("train-list").assertIsDisplayed()
        capture("returned-list")
    }

    @Test fun twentyThousandRowsAreLazyVerticalAndRestorePosition() {
        evaluate("""
            CRTicketAndroid.trainListResult(JSON.stringify({
                date: new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10),
                loading: false, error: '', result: {trains:
                    Array.from({length: 20000}, (_, i) => ({
                        train: 'G' + (i + 1), from: '北京南', to: '上海虹桥',
                        duration: '04:30', stationCount: 8, mileage: 1318
                    }))}
            }));
        """.trimIndent())
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("共 20,000 趟").fetchSemanticsNodes().isNotEmpty()
        }
        val list = compose.onNodeWithTag("train-list")
        assertTrue("Too many composed rows", list.onChildren().fetchSemanticsNodes().size < 30)
        val initial = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertFalse(list.fetchSemanticsNode().config.contains(SemanticsProperties.HorizontalScrollAxisRange))
        list.performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(initial, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        repeat(6) { list.performTouchInput { swipeUp(durationMillis = 120) } }
        compose.waitForIdle()
        val position = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("List did not fling", position > initial + 10)
        assertTrue(list.onChildren().fetchSemanticsNodes().size < 30)
        capture("twenty-thousand-scrolled")
        list.onChildren().onFirst().performClick()
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        pressBack()
        awaitListReady()
        compose.waitForIdle()
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        dragNativeRail(0.25f, 0.5f)
        compose.waitForIdle()
        assertTrue("MD2 scroller did not reach the middle", firstFixtureTrain() in 9500..10500)
        evaluate("""
            window.fastScrollRefreshes = 0;
            window.addEventListener('crticket-train-list-request', () => window.fastScrollRefreshes++);
        """.trimIndent())
        dragNativeRail(0.5f, 1f)
        compose.waitForIdle()
        compose.onNodeWithText("G20000").assertIsDisplayed()
        assertEquals("0", evaluate("window.fastScrollRefreshes"))
        capture("twenty-thousand-last")
        dragNativeRail(0.97f, 0f)
        compose.waitForIdle()
        compose.onNodeWithText("G1", substring = false).assertIsDisplayed()
        dragNativeRail(0f, 0.25f)
        compose.waitForIdle()
        val jumped = firstFixtureTrain()
        assertTrue("MD2 scroller did not reach quarter: $jumped", jumped in 4000..6000)
        assertTrue(list.onChildren().fetchSemanticsNodes().size < 30)
        capture("fast-scroll-quarter")
    }

    private fun firstFixtureTrain(): Int {
        return compose.onNodeWithTag("train-list").onChildren().fetchSemanticsNodes()
            .flatMap {
                if (it.config.contains(SemanticsProperties.Text)) it.config[SemanticsProperties.Text]
                else emptyList()
            }
            .mapNotNull { Regex("^G(\\d+)$").matchEntire(it.text)?.groupValues?.get(1)?.toInt() }
            .minOrNull() ?: error("No fixture row visible")
    }

    private fun awaitListReady() {
        compose.waitUntil(10_000) {
            var ready = false
            instrumentation.runOnMainSync {
                val view = activity.findViewById<android.view.View>(R.id.expressive_train_list_view)
                ready = view.visibility == android.view.View.VISIBLE && view.scaleX == 1f && view.translationX == 0f
            }
            ready && evaluate("location.hash === '#/list'") == "true"
        }
    }

    private fun dragNativeRail(from: Float, to: Float) {
        val bounds = compose.onNodeWithTag("train-list").fetchSemanticsNode().boundsInRoot
        val view = activity.findViewById<android.view.View>(R.id.expressive_train_list_view)
        val density = activity.resources.displayMetrics.density
        val x = view.width - 4f * density
        val top = bounds.top + 1f
        val bottom = bounds.bottom - 1f
        val start = top + from * (bottom - top)
        val end = top + to * (bottom - top)
        val downTime = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int, y: Float) {
            instrumentation.runOnMainSync {
                val event = android.view.MotionEvent.obtain(
                    downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0,
                )
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        touch(android.view.MotionEvent.ACTION_DOWN, start)
        repeat(12) { index ->
            Thread.sleep(16)
            touch(android.view.MotionEvent.ACTION_MOVE, start + (end - start) * (index + 1) / 12)
        }
        touch(android.view.MotionEvent.ACTION_UP, end)
    }

    @Test fun expressiveRefreshAnimatesThenFadesOut() {
        // Compose's default test policy cancels infinite animations when autoAdvance is true.
        compose.mainClock.autoAdvance = false
        // Hold a loading snapshot so the official indeterminate animation can be observed.
        evaluate("""
            window.refreshSceneDate = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
            CRTicketAndroid.trainListResult(JSON.stringify({
                date: window.refreshSceneDate, loading: true, error: ''
            }));
        """.trimIndent())
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodesWithTag("train-list-refresh-indicator").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("正在刷新车次").assertIsDisplayed()
        capture("refresh-animation")
        fun frame(): Bitmap {
            return compose.onNodeWithTag("train-list-refresh-indicator")
                .captureToImage().asAndroidBitmap()
        }
        val first = frame()
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        Thread.sleep(100)
        val second = frame()
        assertFalse("Official refresh indicator did not animate", first.sameAs(second))
        first.recycle()
        second.recycle()
        evaluate("""
            CRTicketAndroid.trainListResult(JSON.stringify({
                date: window.refreshSceneDate, loading: false, error: ''
            }));
        """.trimIndent())
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodesWithTag("train-list-refresh-indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.mainClock.autoAdvance = true
    }

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var value = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { value = it; done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return value
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        // Window enter/exit animations run outside Compose's test clock.
        Thread.sleep(500)
        val folder = File(activity.getExternalFilesDir(null), "expressive-train-list").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
