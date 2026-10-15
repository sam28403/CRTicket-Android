package cc.samlab.ticket

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class TabletNativePagesTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val windows = WindowLayoutInfoPublisherRule()

    @Test fun inspectTabletAndFoldPostures() {
        assertEquals("Pixel Tablet", android.os.Build.MODEL)
        val fixture = TabletPageFixture(compose)
        try {
            for (orientation in listOf<Fold.Orientation?>(null, Fold.Orientation.VERTICAL, Fold.Orientation.HORIZONTAL)) {
                val name = when (orientation) {
                    Fold.Orientation.VERTICAL -> "book"
                    Fold.Orientation.HORIZONTAL -> "tabletop"
                    else -> "tablet"
                }
                val fold = orientation?.let { FoldingFeature(fixture.activity, size = 32, orientation = it) }
                windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOfNotNull(fold)))
                fixture.navigate("/list")
                compose.waitUntil(120_000) {
                    runCatching {
                        compose.onNodeWithTag("train-list-total").fetchSemanticsNode()
                            .config[SemanticsProperties.Text].any { it.text.startsWith("共 ") }
                    }.getOrDefault(false)
                }
                compose.onNodeWithTag("train-list").assertIsDisplayed()
                val detail = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInWindow
                val menu = compose.onNodeWithTag("navigation-menu").fetchSemanticsNode().boundsInWindow
                if (fold != null) {
                    if (orientation == Fold.Orientation.VERTICAL) {
                        assertTrue(menu.right <= fold.bounds.left + 1)
                        assertTrue(detail.left >= fold.bounds.right - 1)
                    } else {
                        assertTrue(menu.bottom <= fold.bounds.top + 1)
                        assertTrue(detail.top >= fold.bounds.bottom - 1)
                    }
                } else assertEquals(1f / 3f, menu.width / (menu.width + detail.width), 0.02f)
                compose.onNode(hasText("车次列表") and hasAnyAncestor(hasTestTag("navigation-menu")))
                    .assertIsSelected()
                assertFalse(compose.onNodeWithTag("train-list").fetchSemanticsNode()
                    .config.contains(SemanticsProperties.HorizontalScrollAxisRange))
                fixture.capture("$name-list")
                compose.onNodeWithTag("train-list").performScrollToIndex(8000)
                assertTrue(compose.onNodeWithTag("train-list").onChildren().fetchSemanticsNodes().size < 30)
                fixture.capture("$name-list-scrolled")
                fixture.navigate("/settings")
                compose.onNodeWithText("到站检测", substring = false).performScrollTo()
                compose.onNodeWithText("接受到访车站的通知").assertIsDisplayed()
                compose.onNodeWithText("到站检测", substring = false).performClick()
                compose.onNodeWithText("启用到站检测").assertIsDisplayed()
                compose.onNodeWithText("接受到访车站的通知").assertDoesNotExist()
                val arrival = compose.onNodeWithTag("arrival-settings-content").fetchSemanticsNode().boundsInWindow
                if (fold != null) {
                    if (orientation == Fold.Orientation.VERTICAL) assertTrue(arrival.left >= fold.bounds.right - 1)
                    else assertTrue(arrival.top >= fold.bounds.bottom - 1)
                }
                fixture.capture("$name-arrival")
                val textBounds = compose.onNodeWithText(
                    "进入车站约 300 米范围时发送到访通知，点按通知可查看当前车站大屏。",
                ).performScrollTo().fetchSemanticsNode().boundsInWindow
                val density = fixture.activity.resources.displayMetrics.density
                assertEquals(arrival.width - 48f * density, textBounds.width, 2f)
                compose.onNodeWithText("管理相关权限").performScrollTo().assertIsDisplayed()
                // Leave settings to reset the tertiary page before the next posture.
                fixture.navigate("/list")
            }
        } finally {
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
            fixture.close()
        }
    }

    @Test fun portraitAndLandscapeKeepListPositionAndArrivalControls() {
        assertEquals("Pixel Tablet", android.os.Build.MODEL)
        val fixture = TabletPageFixture(compose)
        val rotation = fixture.shell("settings get system user_rotation").trim()
        val automatic = fixture.shell("settings get system accelerometer_rotation").trim() == "1"
        val fixed = fixture.shell("wm fixed-to-user-rotation").trim()
        try {
            fixture.shell("wm fixed-to-user-rotation enabled")
            fixture.navigate("/list")
            compose.waitUntil(120_000) {
                runCatching { compose.onNodeWithTag("train-list").onChildren().fetchSemanticsNodes().size > 1 }
                    .getOrDefault(false)
            }
            compose.onNodeWithTag("train-list").performScrollToIndex(8000)
            for ((angle, expected) in listOf(0 to Configuration.ORIENTATION_PORTRAIT, 3 to Configuration.ORIENTATION_LANDSCAPE)) {
                fixture.shell("wm user-rotation lock $angle")
                compose.waitUntil(10_000) { fixture.activity.resources.configuration.orientation == expected }
                compose.waitForIdle()
                val index = compose.onNodeWithTag("train-list").fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange].value()
                assertTrue("Rotation lost list position", index > 0)
                fixture.capture("rotation-$angle-list")
                fixture.navigate("/settings")
                compose.onNodeWithText("到站检测", substring = false).performScrollTo().performClick()
                compose.onNodeWithText("接受到访车站的通知").assertDoesNotExist()
                compose.onNodeWithText("启用到站检测").assertIsDisplayed()
                fixture.capture("rotation-$angle-arrival")
                fixture.navigate("/list")
            }
        } finally {
            fixture.shell(if (automatic) "wm user-rotation free" else "wm user-rotation lock $rotation")
            fixture.shell("wm fixed-to-user-rotation $fixed")
            fixture.close()
        }
    }
}
