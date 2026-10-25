package cc.samlab.ticket

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OrdinaryFareDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var welcome: WelcomeTestState

    @Before fun prepareWelcome() {
        welcome = WelcomeTestState(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After fun restoreWelcome() { welcome.close() }

    @Test fun calculatorGateValidationFullTableAndReturn() {
        val fixture = TabletPageFixture(compose)
        val previous = RailfanPreferences.enabled(fixture.activity)
        try {
            fixture.instrumentation.runOnMainSync { RailfanPreferences.setEnabled(fixture.activity, false) }
            fixture.navigate("/price")
            compose.onNodeWithTag("fare-calculator").assertDoesNotExist()
            fixture.instrumentation.runOnMainSync { RailfanPreferences.setEnabled(fixture.activity, true) }
            compose.onNodeWithTag("fare-train").performTextInput("Z1")
            compose.onNodeWithTag("fare-distance").performTextInput("108")
            assertEquals(compose.onNodeWithTag("fare-train").fetchSemanticsNode().boundsInRoot.top,
                compose.onNodeWithTag("fare-distance").fetchSemanticsNode().boundsInRoot.top, 1f)
            compose.onNodeWithTag("fare-distance").performImeAction()
            val calculator = compose.onNodeWithTag("fare-calculator")
            calculator.performScrollToNode(hasTestTag("fare-result-HARD_SEAT"))
            compose.onNodeWithTag("fare-result-HARD_SEAT").assert(hasAnyDescendant(hasText("¥16.5")))
            assertEquals(compose.onNodeWithTag("fare-result-HARD_SEAT").fetchSemanticsNode().boundsInRoot.top,
                compose.onNodeWithTag("fare-result-SOFT_SEAT").fetchSemanticsNode().boundsInRoot.top, 1f)
            val gridWidth = calculator.fetchSemanticsNode().boundsInRoot.width /
                fixture.activity.resources.displayMetrics.density - 32
            if (gridWidth >= 540) {
                assertEquals(compose.onNodeWithTag("fare-result-HARD_SEAT").fetchSemanticsNode().boundsInRoot.top,
                    compose.onNodeWithTag("fare-result-HARD_LOWER").fetchSemanticsNode().boundsInRoot.top, 1f)
            }
            calculator.performScrollToIndex(0)
            compose.onNodeWithTag("fare-air-NONE").performClick()
            compose.onNodeWithTag("fare-result-HARD_SEAT").assert(hasAnyDescendant(hasText("¥8.5")))
            compose.onNodeWithTag("fare-air-NEW").performClick()
            compose.onNodeWithTag("fare-result-HARD_SEAT").assert(hasAnyDescendant(hasText("¥16.5")))
            capture(fixture, "fare-calculator-result")
            calculator.performScrollToNode(hasTestTag("fare-notice"))
            compose.onNodeWithText(fixture.activity.getString(R.string.fare_calculator_notice)).assertIsDisplayed()
            capture(fixture, "fare-calculator-bottom")
            calculator.performScrollToNode(hasTestTag("fare-open-table"))
            compose.onNodeWithTag("fare-open-table").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("fare-table").fetchSemanticsNodes().isNotEmpty()
            }
            val table = compose.onNodeWithTag("fare-table")
            val tableWidth = table.fetchSemanticsNode().boundsInRoot.width /
                fixture.activity.resources.displayMetrics.density
            if (tableWidth < 600) {
                compose.onNodeWithText(fixture.activity.getString(R.string.fare_distance_short)).assertIsDisplayed()
            }
            assertEquals(compose.onNodeWithTag("fare-grade-selector").fetchSemanticsNode().boundsInRoot.top,
                compose.onNodeWithTag("fare-air-selector").fetchSemanticsNode().boundsInRoot.top, 1f)
            table.performScrollToIndex(4999)
            compose.onNodeWithTag("fare-distance-5000").assertIsDisplayed()
            table.onChildren().onLast().onChildren().fetchSemanticsNodes().forEach { node ->
                val action = node.config[SemanticsActions.GetTextLayoutResult]
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action.action!!.invoke(layouts))
                layouts.forEach { layout ->
                    assertEquals(1, layout.lineCount)
                    assertFalse("Fare value was clipped", layout.hasVisualOverflow)
                }
            }
            capture(fixture, "fare-table-last")
            dragRail(fixture, 1f, 0.5f)
            compose.waitForIdle()
            val middle = table.onChildren().fetchSemanticsNodes().mapNotNull {
                it.config.getOrElse(SemanticsProperties.TestTag) { "" }
                    .removePrefix("fare-distance-").toIntOrNull()
            }.first()
            assertTrue("Fast scroll must reach the middle: $middle", middle in 2300..2700)
            capture(fixture, "fare-table-middle")
            compose.onNodeWithTag("fare-grade-selector").performClick()
            compose.onNodeWithTag("fare-grade-LOCAL").performClick()
            compose.onNodeWithTag("fare-distance-1").assertIsDisplayed()
            compose.onNodeWithTag("fare-air-selector").performClick()
            compose.onNodeWithTag("fare-air-NONE").performClick()
            table.performScrollToIndex(107)
            compose.onNodeWithTag("fare-distance-108")
                .assert(hasAnyDescendant(hasText("6.5")))
            compose.onNodeWithContentDescription(fixture.activity.getString(R.string.about_back)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("fare-calculator").fetchSemanticsNodes().isNotEmpty()
            }
            calculator.performScrollToNode(hasTestTag("fare-result-HARD_SEAT"))
            compose.onNodeWithTag("fare-result-HARD_SEAT").assert(hasAnyDescendant(hasText("¥16.5")))
            calculator.performScrollToIndex(0)
            compose.onNodeWithTag("fare-distance").performTextReplacement("5001")
            compose.onNodeWithTag("fare-distance").performImeAction()
            compose.onNodeWithTag("fare-result-HARD_SEAT").assertDoesNotExist()
            compose.onNodeWithText(fixture.activity.getString(R.string.fare_distance_error)).assertExists()
            compose.onNodeWithTag("fare-train").performTextReplacement("G1")
            compose.onNodeWithTag("fare-calculate").performClick()
            compose.onNodeWithText(fixture.activity.getString(R.string.fare_train_error)).assertExists()
        } finally {
            fixture.instrumentation.runOnMainSync { RailfanPreferences.setEnabled(fixture.activity, previous) }
            fixture.close()
        }
    }

    private fun capture(fixture: TabletPageFixture, name: String) {
        compose.waitForIdle()
        Thread.sleep(350)
        val bitmap = compose.onNodeWithTag("expressive-navigation").captureToImage().asAndroidBitmap()
        val folder = File(fixture.activity.getExternalFilesDir(null), "fare-preview").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun dragRail(fixture: TabletPageFixture, from: Float, to: Float) {
        val bounds = compose.onNodeWithTag("fare-table").fetchSemanticsNode().boundsInRoot
        val view = fixture.activity.findViewById<View>(R.id.expressive_fare_table_view)
        val x = view.width - 4f * fixture.activity.resources.displayMetrics.density
        val thumb = checkNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(
            view.context, me.zhanghai.android.fastscroll.R.drawable.afs_md2_thumb,
        ))
        val top = bounds.top + thumb.intrinsicHeight / 2f
        val bottom = bounds.bottom - thumb.intrinsicHeight / 2f
        val start = top + from * (bottom - top)
        val end = top + to * (bottom - top)
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, y: Float) {
            fixture.instrumentation.runOnMainSync {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, start)
        repeat(12) { index ->
            Thread.sleep(16)
            touch(MotionEvent.ACTION_MOVE, start + (end - start) * (index + 1) / 12)
        }
        touch(MotionEvent.ACTION_UP, end)
    }
}
