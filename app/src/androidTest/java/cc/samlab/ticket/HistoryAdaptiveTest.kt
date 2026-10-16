package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryAdaptiveTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val windows = WindowLayoutInfoPublisherRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun wideAndFoldedHistoryKeepNavigationAndEditorDraft() {
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
            evaluate("""
                window.historyReady = false;
                document.getElementById('app').__vue_app__.config.globalProperties
                    .${'$'}router.isReady().then(() => {
                        window.crticketNavigate('/history'); window.historyReady = true;
                    });
            """.trimIndent())
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
            }
            val first = compose.onNodeWithTag("navigation-menu").fetchSemanticsNode().boundsInWindow
            val second = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInWindow
            assertEquals(1f / 3f, first.width / (first.width + second.width), 0.02f)
            compose.onNode(hasText("历史记录") and hasAnyAncestor(hasTestTag("navigation-menu")))
                .assertIsSelected()
            capture("tablet-history")
            compose.onNodeWithText("补登记录").performClick()
            compose.onNodeWithTag("history-field-trainNo").performTextReplacement("G8888")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            capture("tablet-editor")
            for (orientation in listOf(Fold.Orientation.VERTICAL, Fold.Orientation.HORIZONTAL)) {
                val fold = FoldingFeature(activity, size = 32, orientation = orientation)
                windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
                compose.waitUntil(10_000) {
                    val left = compose.onNodeWithTag("navigation-menu").fetchSemanticsNode().boundsInWindow
                    val right = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInWindow
                    if (orientation == Fold.Orientation.VERTICAL) {
                        left.right <= fold.bounds.left + 1 && right.left >= fold.bounds.right - 1
                    } else left.bottom <= fold.bounds.top + 1 && right.top >= fold.bounds.bottom - 1
                }
                compose.onNodeWithTag("history-field-trainNo").assertTextContains("G8888")
                capture(if (orientation == Fold.Orientation.VERTICAL) "fold-book-editor" else "fold-tabletop-editor")
                windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
            }
            compose.waitForIdle()
            pressBack()
            compose.onNodeWithTag("history-list").assertExists()
            compose.onNodeWithTag("history-editor").assertDoesNotExist()
            assertEquals("true", evaluate("location.hash === '#/history'"))
        } finally {
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

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
        val folder = File(activity.getExternalFilesDir(null), "history-preview").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
