package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun createAndEditUseAllSpaceAboveKeyboardAndKeepFieldsReachable() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
        }
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
            evaluate("""
                document.getElementById('app').__vue_app__.config.globalProperties
                    .${'$'}router.isReady().then(() => window.crticketNavigate('/history'));
            """.trimIndent())
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag("history-pull").fetchSemanticsNode()
                        .config[SemanticsProperties.StateDescription] == "历史记录已加载"
                }.getOrDefault(false)
            }
            // Display an isolated native fixture. Never save, edit or delete device records.
            evaluate("""
                CRTicketAndroid.historyResult(JSON.stringify({loading: false, error: '', rows: [{
                    id: 'keyboard-fixture', number: 'E123456789', trainNo: 'G25',
                    from: '北京南', to: '上海虹桥', date: '2026年10月16日', time: '08:00',
                    price: 553, seatNo: '03车12A号', seatType: '二等座'
                }]}));
            """.trimIndent())
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-record-keyboard-fixture").fetchSemanticsNodes().isNotEmpty()
            }
            for (mode in listOf("create", "edit")) {
                if (mode == "create") compose.onNodeWithText("补登记录").performClick()
                else {
                    compose.onNodeWithContentDescription("记录操作").performClick()
                    compose.onNodeWithText("编辑记录").performClick()
                }
                compose.onNodeWithTag("history-field-trainNo").performClick()
                compose.waitUntil(10_000) { keyboardVisible() }
                compose.waitForIdle()
                capture("$mode-keyboard")
                assertViewportFillsEditor()
                compose.onNodeWithTag("history-field-trainNo").performTextReplacement("G8888")
                compose.onNodeWithTag("history-field-seatNo").performScrollTo().performClick()
                compose.onNodeWithTag("history-field-seatNo").performTextReplacement("03车12A号")
                assertFieldInsideViewport("seatNo")
                compose.onNodeWithTag("history-field-message").performScrollTo().performClick()
                compose.onNodeWithTag("history-field-message").performTextInput("键盘检查")
                assertFieldInsideViewport("message")
                capture("$mode-keyboard-bottom")
                closeSoftKeyboard()
                compose.waitUntil(10_000) { !keyboardVisible() }
                compose.onNodeWithTag("history-field-trainNo").performScrollTo().assertTextContains("G8888")
                compose.onNodeWithContentDescription("返回").performClick()
                compose.onNodeWithTag("history-list").assertExists()
            }
        } finally {
            closeSoftKeyboard()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun assertViewportFillsEditor() {
        val editor = compose.onNodeWithTag("history-editor").fetchSemanticsNode().boundsInWindow
        val scroll = compose.onNodeWithTag("history-editor-scroll").fetchSemanticsNode().boundsInWindow
        assertEquals("The form must use the full area above the IME", editor.bottom, scroll.bottom, 2f)
    }

    private fun assertFieldInsideViewport(key: String) {
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag("history-editor-scroll").fetchSemanticsNode().boundsInWindow
        val field = compose.onNodeWithTag("history-field-$key").fetchSemanticsNode().boundsInWindow
        assertTrue("$key must remain visible above the IME", field.height > 0 && field.top >= scroll.top - 2f && field.bottom <= scroll.bottom + 2f)
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
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
        val folder = File(activity.getExternalFilesDir(null), "history-keyboard").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
