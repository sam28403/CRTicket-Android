package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class TabletPageFixture(private val compose: ComposeTestRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val activity = launch()

    private fun launch(): MainActivity {
        cc.samlab.ticket.ui.welcome.WelcomeState.complete(instrumentation.targetContext)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        return instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
    }

    init {
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(60_000) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                    activity.window.decorView.hasWindowFocus()
            }
            ready
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate && window.crticketSettings)") == "true" }
    }

    fun navigate(route: String) {
        evaluate("window.crticketNavigate('$route')")
        compose.waitUntil(20_000) { evaluate("location.hash.split('?')[0] === '#$route'") == "true" }
        compose.waitForIdle()
        Thread.sleep(400)
    }

    fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var value = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { value = it; latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return value
    }

    fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val folder = File(activity.getExternalFilesDir(null), "tablet-native-pages").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }

    fun close() { instrumentation.runOnMainSync { activity.finish() } }
}
