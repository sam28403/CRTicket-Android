package cc.samlab.ticket

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import kotlin.math.abs

internal fun assertReportKeyboardViewport(
    compose: ComposeTestRule,
    scenario: ActivityScenario<MainActivity>,
) {
    compose.waitUntil(10_000) {
        var visible = false
        scenario.onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        visible
    }
    // The native host has already removed the IME area. The form should fill
    // the remaining space, including while the keyboard animation settles.
    fun gap(): Float {
        val page = compose.onNodeWithTag("navigation-page:/about/report")
            .fetchSemanticsNode().boundsInWindow
        val viewport = compose.onNodeWithTag("report-viewport")
            .fetchSemanticsNode().boundsInWindow
        return page.bottom - viewport.bottom
    }
    runCatching { compose.waitUntil(5_000) { abs(gap()) <= 2f } }
    assertEquals("The form must fill the available space above the keyboard", 0f, gap(), 2f)
}
