package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StatisticsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val windows = WindowLayoutInfoPublisherRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private var originalTickets: String? = null

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
        val saved = JSONTokener(evaluate("localStorage.getItem('crticket-device-tickets-v1')")).nextValue()
        originalTickets = saved as? String
    }

    @After fun close() {
        windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
        if (::activity.isInitialized) {
            val saved = originalTickets
            evaluate(if (saved == null) "localStorage.removeItem('crticket-device-tickets-v1')"
                else "localStorage.setItem('crticket-device-tickets-v1', ${JSONObject.quote(saved)})")
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun emptyMorphAndSettingsTitleExpandWithoutRefresh() {
        fixture(empty = true)
        compose.onNodeWithTag("statistics-empty").assertIsDisplayed()
        compose.onNodeWithTag("statistics-empty-artwork").assertIsDisplayed()
        compose.onNodeWithText("暂无运转数据").assertIsDisplayed()
        capture("empty-collapsed")
        expandHeader("statistics-empty")
        capture("empty-expanded")
        compose.onNodeWithTag("statistics-empty").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { headerState() == "已收起" }
        val started = evaluate("performance.timeOrigin")
        evaluate("window.CRTicketAndroid.setPullRefreshProgress(1); window.CRTicketAndroid.refreshPage()")
        instrumentation.runOnMainSync {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val refresh = content.findViewWithTag<View>("expressive-pull-refresh")
            assertEquals(View.GONE, refresh.visibility)
        }
        assertEquals(started, evaluate("performance.timeOrigin"))
        assertEquals("false", evaluate("Boolean(document.querySelector('.el-empty'))"))
    }

    @Test fun populatedChartsScrollAndTitleExpandWithoutLosingData() {
        fixture(empty = false)
        compose.onNodeWithTag("statistics-empty").assertDoesNotExist()
        compose.waitUntil(20_000) {
            evaluate("Boolean(document.querySelector('.chart-container canvas'))") == "true"
        }
        assertEquals("true", evaluate("document.querySelector('.user-page').hasAttribute('data-pull-refresh-disabled')"))
        assertEquals("false", evaluate("Boolean(document.querySelector('.map-container canvas'))"))
        val chart = evaluate("document.querySelector('.chart-container').getAttribute('_echarts_instance_')")
        expandHeader("statistics-screen")
        capture("data-expanded")
        compose.onNodeWithTag("statistics-screen").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { headerState() == "已收起" }
        assertEquals(chart, evaluate("document.querySelector('.chart-container').getAttribute('_echarts_instance_')"))
        evaluate("document.querySelector('.map-container').scrollIntoView()")
        compose.waitUntil(20_000) {
            evaluate("Boolean(document.querySelector('.map-container canvas'))") == "true"
        }
        capture("data-map")
    }

    @Test fun expressiveMonthPickerConfirmsCancelsAndUpdatesDailyCounts() {
        fixture(empty = false)
        evaluate("window.dispatchEvent(new CustomEvent('crticket-statistics-month', {detail:'2026-10'}))")
        compose.waitUntil(10_000) {
            evaluate("document.querySelector('.calendar-records')?.textContent.includes('2400次')") == "true"
        }
        fun openPicker() {
            evaluate("document.querySelector('.operation-month-button').scrollIntoView({block:'center'})")
            evaluate("document.querySelector('.operation-month-button').click()")
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("statistics-month-picker").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val original = evaluate("document.querySelector('.operation-month-button').textContent")
        openPicker()
        compose.onNodeWithContentDescription("下一年").performClick()
        compose.onNodeWithText("取消", substring = false).performClick()
        assertEquals(original, evaluate("document.querySelector('.operation-month-button').textContent"))
        openPicker()
        compose.onNodeWithTag("statistics-month-year").assertTextEquals("2026年")
        compose.onNodeWithTag("statistics-month-9").performClick()
        capture("expressive-month-picker")
        compose.onNodeWithText("确定", substring = false).performClick()
        compose.waitUntil(10_000) {
            evaluate("document.querySelector('.operation-month-button').textContent.includes('09月')") == "true" &&
                evaluate("Boolean(document.querySelector('.calendar-empty'))") == "true"
        }
        assertEquals("0", evaluate("document.querySelectorAll('.calendar-records li').length"))
        openPicker()
        compose.onNodeWithTag("statistics-month-10").performClick()
        compose.onNodeWithText("确定", substring = false).performClick()
        compose.waitUntil(10_000) {
            evaluate("document.querySelector('.calendar-records')?.textContent.replace(/\\s/g, '') === '10月17日2400次'") == "true"
        }
        assertEquals("none", evaluate("getComputedStyle(document.querySelector('.calendar-container')).pointerEvents").trim('"'))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("statistics-month-picker").fetchSemanticsNodes().isEmpty()
        }
        // Allow the platform dialog exit and queued canvas frame to finish before capture.
        android.os.SystemClock.sleep(400)
        capture("calendar-daily-counts")
    }

    @Test fun tabletAndFoldedLayoutsKeepChartAndAvoidHinge() {
        Assume.assumeTrue(activity.resources.configuration.screenWidthDp >= 840)
        fixture(empty = false)
        compose.waitUntil(20_000) {
            evaluate("Boolean(document.querySelector('.chart-container canvas'))") == "true"
        }
        val chart = evaluate("document.querySelector('.chart-container').getAttribute('_echarts_instance_')")
        capture("tablet-data")
        for (orientation in listOf(Fold.Orientation.VERTICAL, Fold.Orientation.HORIZONTAL)) {
            val fold = FoldingFeature(activity, size = 32, orientation = orientation)
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
            compose.waitUntil(10_000) {
                val first = compose.onNodeWithTag("navigation-menu").fetchSemanticsNode().boundsInWindow
                val second = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInWindow
                if (orientation == Fold.Orientation.VERTICAL) {
                    first.right <= fold.bounds.left + 1 && second.left >= fold.bounds.right - 1
                } else first.bottom <= fold.bounds.top + 1 && second.top >= fold.bounds.bottom - 1
            }
            compose.onNodeWithTag("statistics-screen").assertIsDisplayed()
            assertEquals(chart, evaluate("document.querySelector('.chart-container').getAttribute('_echarts_instance_')"))
            capture(if (orientation == Fold.Orientation.VERTICAL) "fold-book" else "fold-tabletop")
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
        }
        fixture(empty = true)
        val fold = FoldingFeature(activity, size = 32, orientation = Fold.Orientation.VERTICAL)
        windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        compose.onNodeWithTag("statistics-empty").assertIsDisplayed()
        capture("fold-empty")
    }

    private fun fixture(empty: Boolean) {
        evaluate("window.crticketNavigate('/home')")
        compose.waitUntil(10_000) { evaluate("location.hash") == "\"#/home\"" }
        evaluate("""
            localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(
                ${if (empty) "[]" else """
                Array.from({length: 2400}, (_, index) => ({
                    id: 'stats-' + index,
                    departure_station: ['北京南','南京南','上海虹桥','杭州东','广州南','沈阳北'][index % 6],
                    arrival_station: ['上海虹桥','郑州东','深圳北','武汉','长沙南'][index % 5],
                    travel_date: '2026年10月17日', departure_time: '09:00', train_no: 'G1'
                }))
                """.trimIndent()}
            ));
            window.crticketNavigate('/user');
        """.trimIndent())
        compose.waitUntil(20_000) {
            var shown = false
            instrumentation.runOnMainSync {
                val view = activity.findViewById<View>(R.id.expressive_navigation_view)
                shown = view.isShown && view.height > 0 && view.alpha == 1f
            }
            shown && evaluate("Boolean(document.querySelector('.user-main'))") == "true"
        }
        if (empty) compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("statistics-empty").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun expandHeader(tag: String) {
        evaluate("window.scrollTo(0, 0)")
        compose.onNodeWithTag(tag).performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(12f, height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(12f, height * 0.8f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(5_000) { headerState() == "已展开" }
    }

    private fun headerState() = compose.onNodeWithTag("statistics-header")
        .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { result = it; done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(activity.getExternalFilesDir(null), "statistics-scenes").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
