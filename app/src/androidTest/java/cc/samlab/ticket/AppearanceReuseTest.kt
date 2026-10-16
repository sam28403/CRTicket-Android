package cc.samlab.ticket

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.home.homeColorScheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppearanceReuseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test
    fun navigationReusesPaletteAndReloadRestoresDocumentColors() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val dark = homeColorScheme(activity, true)
                assertSame(dark, homeColorScheme(activity, true))
                assertNotSame(dark, homeColorScheme(activity, false))
                assertEquals(Color.Black, homeColorScheme(activity, true, true).surface)
                val configuration = Configuration(activity.resources.configuration).apply {
                    fontScale += 0.01f
                }
                val changed = activity.createConfigurationContext(configuration)
                assertNotSame(dark, homeColorScheme(changed, true))
            }
            await { evaluate("Boolean(window.crticketNavigate)") == "true" }
            Thread.sleep(800)
            evaluate("""
                window.paletteEvents = 0;
                window.addEventListener('crticket-native-palette', () => window.paletteEvents++);
            """.trimIndent())
            for (route in listOf("/settings", "/about", "/home", "/")) {
                evaluate("window.crticketNavigate(${JSONObject.quote(route)})")
                Thread.sleep(600)
            }
            assertEquals(
                "Unchanged navigation must not resend the palette",
                "0",
                evaluate("window.paletteEvents"),
            )
            val colorScript = "getComputedStyle(document.documentElement)" +
                ".getPropertyValue('--el-color-primary')"
            val original = evaluate(colorScript)
            assertTrue("Expected an applied primary color", original.trim('"', ' ').isNotEmpty())
            val document = evaluate("performance.timeOrigin")
            instrumentation.runOnMainSync { activity.webView.reload() }
            await {
                // WebView can discard callbacks belonging to the document being replaced.
                runCatching {
                    evaluate("performance.timeOrigin", 1_000) != document &&
                        evaluate("Boolean(window.crticketNavigate)", 1_000) == "true"
                }.getOrDefault(false)
            }
            await { evaluate(colorScript) == original }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Appearance did not become ready")
    }

    private fun evaluate(script: String, timeoutMillis: Long = 10_000): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                result = it
                latch.countDown()
            }
        }
        assertTrue("JavaScript callback: $script", latch.await(timeoutMillis, TimeUnit.MILLISECONDS))
        return result
    }
}
