package cc.samlab.ticket

import android.content.Intent
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    @Test fun webThemeTracksAndroidConfigurationWithoutReloading() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity

        fun evaluate(script: String): String {
            val done = CountDownLatch(1)
            var value = "null"
            instrumentation.runOnMainSync {
                activity.webView.evaluateJavascript(script) {
                    value = it
                    done.countDown()
                }
            }
            assertTrue("WebView callback timed out for: $script", done.await(15, TimeUnit.SECONDS))
            return value
        }

        fun waitReady() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (evaluate("Boolean(window.crticketBack)") != "true" && System.nanoTime() < deadline) {
                Thread.sleep(200)
            }
            assertEquals("true", evaluate("Boolean(window.crticketBack)"))
            instrumentation.waitForIdleSync()
        }

        fun changeSystem(dark: Boolean) {
            val config = Configuration(activity.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            instrumentation.runOnMainSync { activity.onConfigurationChanged(config) }
            Thread.sleep(250)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            val expected = dark.toString()
            while (evaluate("document.documentElement.classList.contains('dark')") != expected &&
                System.nanoTime() < deadline) {
                Thread.sleep(100)
            }
            assertEquals(expected, evaluate("document.documentElement.classList.contains('dark')"))
            assertEquals(expected, evaluate("CRTicketAndroid.systemDarkMode()"))
        }

        waitReady()
        val savedPreference = evaluate("localStorage.getItem('crticket-theme')")
        try {
            evaluate("""
                window.beforeThemeReload = true;
                localStorage.setItem('crticket-theme','classic-system');
                location.reload();
            """.trimIndent())
            val reloadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            val ready = "Boolean(!window.beforeThemeReload && window.crticketBack && " +
                "document.readyState === 'complete')"
            while (evaluate(ready) != "true" && System.nanoTime() < reloadDeadline) {
                Thread.sleep(200)
            }
            assertEquals("true", evaluate(ready))
            waitReady()
            assertEquals("0", evaluate("document.querySelectorAll('.android-toolbar').length"))
            changeSystem(true)
            changeSystem(false)
            assertEquals("\"classic-system\"", evaluate("localStorage.getItem('crticket-theme')"))
        } finally {
            evaluate("""
                const savedTheme = $savedPreference;
                if (savedTheme === null) localStorage.removeItem('crticket-theme');
                else localStorage.setItem('crticket-theme', savedTheme);
            """.trimIndent())
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
