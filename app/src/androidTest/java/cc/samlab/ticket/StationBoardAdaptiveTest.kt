package cc.samlab.ticket

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationBoardAdaptiveTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val windows = WindowLayoutInfoPublisherRule()

    @Test
    fun compactHeaderAndAdaptiveCardsInRealWindowAndFoldPanes() {
        val fixture = TabletPageFixture(compose)
        val tablet = Build.MODEL == "Pixel Tablet"
        val originalOrientation = fixture.activity.requestedOrientation
        try {
            fixture.instrumentation.runOnMainSync {
                fixture.activity.requestedOrientation = if (tablet) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(15_000) {
                fixture.activity.resources.configuration.orientation == if (tablet) {
                    Configuration.ORIENTATION_LANDSCAPE
                } else Configuration.ORIENTATION_PORTRAIT
            }
            fixture.navigate("/station")
            fixture.evaluate("window.crticketNavigate('/station?station=' + encodeURIComponent('北京南'))")
            compose.waitUntil(120_000) {
                runCatching {
                    compose.onNodeWithTag("station-tab-all").fetchSemanticsNode()
                    compose.onNodeWithTag("station-board").onChildren().fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            compose.onNodeWithTag("station-follow").assertIsOn().performClick()
            for (tab in listOf("all", "departure", "arrival")) {
                compose.onNodeWithTag("station-tab-$tab").performClick()
                compose.onNodeWithTag("station-board").performScrollToIndex(0)
                val columns = checkLayout(fixture, "${if (tablet) "tablet" else "phone"}-$tab")
                if (tablet) assertTrue("Tablet must show multiple columns", columns >= 2)
                else assertEquals("Phone must keep readable single-column cards", 1, columns)
            }
            if (tablet) {
                compose.onNodeWithTag("station-tab-all").performClick()
                for (orientation in listOf(Fold.Orientation.VERTICAL, Fold.Orientation.HORIZONTAL)) {
                    val fold = FoldingFeature(fixture.activity, size = 32, orientation = orientation)
                    windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
                    compose.waitForIdle()
                    compose.onNodeWithTag("station-board").performScrollToIndex(0)
                    val name = if (orientation == Fold.Orientation.VERTICAL) "book" else "tabletop"
                    val columns = checkLayout(fixture, name)
                    val pane = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInWindow
                    val grid = compose.onNodeWithTag("station-board").fetchSemanticsNode().boundsInWindow
                    assertTrue(grid.left >= pane.left && grid.right <= pane.right + 1)
                    if (orientation == Fold.Orientation.VERTICAL) {
                        assertTrue("Cards crossed the hinge", grid.left >= fold.bounds.right - 1)
                    } else {
                        assertTrue("Cards crossed the hinge", grid.top >= fold.bounds.bottom - 1)
                        assertTrue("Wide tabletop pane must support at least three columns", columns >= 3)
                    }
                }
            }
        } finally {
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(emptyList()))
            fixture.instrumentation.runOnMainSync {
                fixture.activity.requestedOrientation = originalOrientation
            }
            fixture.close()
        }
    }

    private fun checkLayout(fixture: TabletPageFixture, name: String): Int {
        compose.waitForIdle()
        val screen = compose.onNodeWithTag("station-screen").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("station-controls").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("station-board")
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val cards = grid.onChildren().fetchSemanticsNodes().filter {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("station-card-")
        }
        assertTrue("No visible cards", cards.isNotEmpty())
        val firstTop = cards.minOf { it.boundsInRoot.top }
        val firstRow = cards.filter { abs(it.boundsInRoot.top - firstTop) < 2f }
        firstRow.zipWithNext().forEach { (left, right) ->
            assertTrue("Cards overlap", left.boundsInRoot.right <= right.boundsInRoot.left)
        }
        assertTrue(gridBounds.top >= controls.bottom - 1)
        assertTrue("Grid needs usable height", gridBounds.height > 80 * fixture.activity.resources.displayMetrics.density)
        val density = fixture.activity.resources.displayMetrics.density
        val fontScale = fixture.activity.resources.configuration.fontScale
        if (screen.height / density >= 650 && fontScale <= 1.1f) {
            assertTrue("Top controls still occupy too much height", controls.height / screen.height < 0.28f)
        }
        compose.onNodeWithTag("station-query").assertIsDisplayed()
        compose.onNodeWithTag("station-submit").assertIsDisplayed()
        compose.onNodeWithText("定位到当前时间").assertDoesNotExist()
        compose.onNodeWithText("跟随当前时间").assertIsDisplayed()
        fixture.capture("station-compact-$name")
        fixture.instrumentation.sendStatus(2, Bundle().apply {
            putString(
                "stream",
                "$name: ${firstRow.size} columns, controls=${controls.height / density}dp, " +
                    "screen=${screen.width / density}x${screen.height / density}dp, " +
                    "headerRatio=${controls.height / screen.height}\n",
            )
        })
        return firstRow.size
    }
}
