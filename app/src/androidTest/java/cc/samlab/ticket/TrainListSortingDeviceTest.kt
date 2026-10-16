package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
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
class TrainListSortingDeviceTest {
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
        // Exercise the native page with fixed data, without waiting for a railway download.
        evaluate("CRTicketAndroid.setNativePage('/list')")
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("train-list").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        evaluate("""
            window.sortFixture = Array.from({length: 20000}, (_, i) => ({
                train: 'G' + (i + 1), from: '北京南', to: '上海虹桥',
                duration: '02:00', stationCount: 10 + i, mileage: 1000 + i
            }));
            Object.assign(window.sortFixture[0], {mileage: 10, stationCount: 20});
            Object.assign(window.sortFixture[1], {mileage: 2, stationCount: 3});
            Object.assign(window.sortFixture[2], {mileage: 2, duration: '01:00', stationCount: 3});
            Object.assign(window.sortFixture[3], {mileage: null, stationCount: null});
            window.sendSortFixture = () => CRTicketAndroid.trainListResult(JSON.stringify({
                date: new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10),
                loading: false, error: '', result: {trains: window.sortFixture}
            }));
            window.sendSortFixture();
        """.trimIndent())
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("共 20,000 趟").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun close() {
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun allFourSortModesAndDirectionsWorkWithTwentyThousandRows() {
        assertFirst("G1")
        choose("按里程")
        assertFirst("G20000")
        capture("mileage-descending")
        choose("升序")
        assertFirst("G2")
        choose("按均速")
        assertFirst("G20000")
        choose("升序")
        assertFirst("G2")
        choose("按站数")
        assertFirst("G20000")
        choose("升序")
        assertFirst("G2")
        choose("降序")
        assertFirst("G20000")
        choose("按车次")
        assertFirst("G1")
        choose("降序")
        assertFirst("G20000")

        choose("按里程")
        assertFirst("G20000")
        choose("升序")
        assertFirst("G2")
        // A new data snapshot must keep the user's sort selection.
        evaluate("window.sendSortFixture()")
        assertFirst("G2")
        val list = compose.onNodeWithTag("train-list")
        assertFalse(list.fetchSemanticsNode().config.contains(SemanticsProperties.HorizontalScrollAxisRange))
        assertTrue(list.onChildren().fetchSemanticsNodes().size < 30)
        list.performScrollToIndex(19999)
        compose.onNodeWithText("G4", substring = false).assertIsDisplayed()
        choose("降序")
        assertFirst("G20000")
        list.performScrollToIndex(19999)
        compose.onNodeWithText("G4", substring = false).assertIsDisplayed()

        // Sorting resets a previously scrolled list and is preserved after viewing a train.
        choose("升序")
        assertFirst("G2")
        list.onChildren().onFirst().performClick()
        compose.waitUntil(10_000) { evaluate("location.hash.startsWith('#/train?')") == "true" }
        pressBack()
        compose.waitUntil(10_000) { evaluate("location.hash === '#/home'") == "true" }
        evaluate("CRTicketAndroid.setNativePage('/list')")
        assertFirst("G2")
        compose.onNodeWithContentDescription("列表排序").performClick()
        compose.onNodeWithText("按里程").assertIsSelected()
        compose.onNodeWithText("升序").assertIsSelected()
        capture("sort-menu")
    }

    private fun choose(label: String) {
        compose.onNodeWithContentDescription("列表排序").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun assertFirst(train: String) {
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("train-list").onChildren().onFirst().fetchSemanticsNode()
                    .config[SemanticsProperties.Text].any { it.text == train }
            }.getOrDefault(false)
        }
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
        compose.waitForIdle()
        val folder = File(activity.getExternalFilesDir(null), "train-list-sorting").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
