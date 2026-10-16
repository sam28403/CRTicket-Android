package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpressiveAppearanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun appearanceModesBlackSurfacesAndScrollTitleStayInSync() {
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
        evaluate("window.crticketNavigate('/home')")
        compose.waitUntil(20_000) {
            runCatching {
                compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        val original = evaluate("localStorage.getItem('crticket-theme') || 'classic-system'")
        try {
            openSettings()
            val smallHeight = compose.onNodeWithTag("settings-header").fetchSemanticsNode().boundsInRoot.height
            compose.onNodeWithText("切换主题").performScrollTo().performClick()
            compose.onAllNodesWithText("跟随系统").onLast().assertExists()
            compose.onAllNodesWithText("亮色").onLast().assertExists()
            compose.onAllNodesWithText("暗色").onLast().performClick()
            compose.waitUntil(10_000) { evaluate("document.documentElement.classList.contains('dark')") == "true" }
            waitReady()
            if (evaluate("document.documentElement.classList.contains('high-contrast')") == "true") {
                compose.onNodeWithContentDescription("纯黑背景开关").performScrollTo().performClick()
                waitReady()
            }
            compose.waitUntil(10_000) {
                evaluate("localStorage.getItem('crticket-theme')") == "\"classic-dark\""
            }
            waitReady()
            assertTrue(rootColor() != Color.BLACK)
            capture("settings-dark")
            compose.onNodeWithContentDescription("纯黑背景开关").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                evaluate("localStorage.getItem('crticket-theme')") == "\"contrast-dark\"" && rootColor() == Color.BLACK
            }
            waitReady()
            capture("settings-black")
            compose.onNodeWithText("导入备份").performScrollTo()
            compose.waitForIdle()
            assertEquals(smallHeight, compose.onNodeWithTag("settings-header").fetchSemanticsNode().boundsInRoot.height)
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/home\"" }
            openSettings()
            assertEquals(smallHeight, compose.onNodeWithTag("settings-header").fetchSemanticsNode().boundsInRoot.height)
            compose.onNodeWithTag("settings-content").performTouchInput { swipeDown() }
            compose.waitForIdle()
            val expandedHeight = compose.onNodeWithTag("settings-header").fetchSemanticsNode().boundsInRoot.height
            assertTrue(expandedHeight > smallHeight)
            capture("settings-title-expanded")
            compose.onNodeWithTag("settings-content").performTouchInput { swipeUp() }
            compose.waitForIdle()
            assertEquals(smallHeight, compose.onNodeWithTag("settings-header").fetchSemanticsNode().boundsInRoot.height)
            capture("settings-title-collapsed")
            compose.onNodeWithText("到站检测").performScrollTo().performClick()
            compose.onNodeWithText("关闭", substring = false).assertDoesNotExist()
        } finally {
            evaluate("window.crticketSettings('restore-theme','theme',{" +
                "mode:($original).split('-').at(-1),pureBlack:($original).startsWith('contrast-')}); true")
            compose.waitUntil(10_000) { evaluate("localStorage.getItem('crticket-theme')") == original }
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun openSettings() {
        compose.onNodeWithText("设置").performScrollTo().performClick()
        compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/settings\"" }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("正在读取…").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        waitReady()
    }

    private fun waitReady() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            !compose.onNodeWithContentDescription("纯黑背景开关").fetchSemanticsNode()
                .config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun rootColor(): Int {
        var color = 0
        instrumentation.runOnMainSync {
            val page = activity.findViewById<View>(R.id.expressive_settings_view)
            color = ((page.parent as View).background as ColorDrawable).color
        }
        return color
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
        val folder = File(activity.getExternalFilesDir(null), "expressive-settings-scenes").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
