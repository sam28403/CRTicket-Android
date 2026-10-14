package cc.samlab.ticket

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AdaptiveWindowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val windows = WindowLayoutInfoPublisherRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun wideMenuDefaultsToAboutAndWebDestinationsUseFullWindow() {
        launch()
        try {
            compose.onNodeWithTag("navigation-shell").assertExists()
            menu("关于").assertIsSelected()
            compose.onNodeWithText("GitHub").assertExists()
            compose.onNodeWithText("车站版本 ${BuildConfig.STATION_DATA_VERSION}").assertExists()
            compose.onNodeWithText("路路通版本", substring = true).assertExists()
            val left = compose.onNodeWithTag("navigation-menu").fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("navigation-detail").fetchSemanticsNode().boundsInRoot
            assertEquals(1f / 3f, left.width / (left.width + right.width), 0.02f)
            capture("tablet-navigation-about")
            menu("设置").performScrollTo().performClick()
            readySettings()
            menu("设置").assertIsSelected()
            compose.onNodeWithText("显示名称").assertIsDisplayed()
            capture("tablet-navigation-settings")
            menu("关于").performScrollTo()
            val menuPosition = menu("关于").fetchSemanticsNode().boundsInRoot.top
            menu("关于").performClick()
            compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/about\"" }
            compose.waitForIdle()
            menu("关于").assertIsSelected()
            assertEquals(menuPosition, menu("关于").fetchSemanticsNode().boundsInRoot.top, 1f)
            menu("车票生成").performScrollTo().performClick()
            compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/\"" }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("navigation-menu").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("navigation-menu").assertDoesNotExist()
            assertWebFillsWindow()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/about\"" }
            menu("关于").assertIsSelected()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun compactAndWideWindowsKeepSettingsDraftAndNavigation() {
        launch()
        val rotation = shell("settings get system user_rotation").trim()
        val automatic = shell("settings get system accelerometer_rotation").trim() == "1"
        val fixed = shell("wm fixed-to-user-rotation").trim()
        try {
            shell("wm fixed-to-user-rotation enabled")
            navigate("/settings")
            readySettings()
            compose.onNodeWithText("显示名称").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextClearance()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("G8888 编辑草稿")
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("navigation-shell").assertDoesNotExist()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("G8888 编辑草稿")
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            compose.onNodeWithTag("navigation-shell").assertExists()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("G8888 编辑草稿")
            compose.onNodeWithText("取消").performClick()
            shell("am start --windowingMode 5 --activity-single-top -n cc.samlab.ticket/.MainActivity")
            shell("am task resize ${activity.taskId} 40 80 1140 1480")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("navigation-shell").fetchSemanticsNodes().isEmpty()
            }
            capture("tablet-navigation-narrow")
            compose.onNodeWithText("显示名称").performScrollTo().performClick()
            shell("am task resize ${activity.taskId} 40 80 2440 1480")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("navigation-shell").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText("修改显示名称").assertExists()
            compose.onNodeWithText("取消").performClick()
            capture("tablet-navigation-resized")
        } finally {
            shell("am start --windowingMode 1 --activity-single-top -n cc.samlab.ticket/.MainActivity")
            shell(if (automatic) "wm user-rotation free" else "wm user-rotation lock $rotation")
            shell("wm fixed-to-user-rotation $fixed")
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun foldUsesHalfPanesAndLegacyWebStillUsesFullWindow() {
        launch()
        try {
            for (orientation in listOf(Fold.Orientation.VERTICAL, Fold.Orientation.HORIZONTAL)) {
                navigate("/settings")
                val fold = FoldingFeature(activity, size = 32, orientation = orientation)
                windows.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
                compose.waitUntil(10_000) {
                    val first = compose.onAllNodesWithTag("navigation-menu").fetchSemanticsNodes()
                        .singleOrNull()?.boundsInWindow
                    val second = compose.onAllNodesWithTag("navigation-detail").fetchSemanticsNodes()
                        .singleOrNull()?.boundsInWindow
                    first != null && second != null &&
                        if (orientation == Fold.Orientation.VERTICAL) {
                            first.right <= fold.bounds.left + 1 && second.left >= fold.bounds.right - 1
                        } else {
                            first.bottom <= fold.bounds.top + 1 && second.top >= fold.bounds.bottom - 1
                        }
                }
                capture(if (orientation == Fold.Orientation.VERTICAL) "navigation-fold-book" else "navigation-fold-tabletop")
                navigate("/history")
                assertWebFillsWindow()
                windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
            }
        } finally {
            windows.overrideWindowLayoutInfo(TestWindowLayoutInfo())
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun menu(text: String) = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("navigation-menu")),
    )

    private fun readySettings() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("纯黑背景开关").fetchSemanticsNodes()
                .singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.waitForIdle()
    }

    private fun assertWebFillsWindow() {
        instrumentation.runOnMainSync {
            val parent = activity.webView.parent as android.view.View
            assertEquals(parent.width - parent.paddingLeft - parent.paddingRight, activity.webView.width)
            assertEquals(parent.height - parent.paddingTop - parent.paddingBottom, activity.webView.height)
        }
    }

    private fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketSettings)") == "true" }
    }

    private fun rotate(request: Int, expected: Int) {
        // Large-screen Android ignores Activity.requestedOrientation. Rotate the test display.
        val rotation = if (request == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) 0 else 3
        shell("wm user-rotation lock $rotation")
        compose.waitUntil(10_000) { activity.resources.configuration.orientation == expected }
        compose.waitForIdle()
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }

    private fun navigate(route: String) {
        evaluate("window.crticketNavigate('$route'); true")
        compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"$route\"" }
        compose.waitForIdle()
        if (route == "/settings") readySettings()
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
        val directory = File(activity.getExternalFilesDir(null), "adaptive-window-scenes").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(
            activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888,
        )
        val copied = CountDownLatch(1)
        var result = -1
        instrumentation.runOnMainSync {
            android.view.PixelCopy.request(activity.window, bitmap, {
                result = it
                copied.countDown()
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        assertTrue(copied.await(10, TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, result)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
