package cc.samlab.ticket

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class WebViewTouchFixture(private val instrumentation: Instrumentation) {
    private val wasInteractive = instrumentation.targetContext
        .getSystemService(PowerManager::class.java).isInteractive
    val activity: MainActivity
    private var downTime = 0L

    init {
        shell("input keyevent KEYCODE_WAKEUP")
        cancelTouch()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        instrumentation.waitForIdleSync()
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun cancelTouch() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            instrumentation.uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var value = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                value = it
                done.countDown()
            }
        }
        check(done.await(15, TimeUnit.SECONDS)) { "WebView callback timed out" }
        return value
    }

    fun touch(action: Int, x: Float, y: Float) {
        val location = IntArray(2)
        var scale = 1f
        instrumentation.runOnMainSync {
            activity.webView.getLocationOnScreen(location)
            scale = activity.resources.displayMetrics.density
        }
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
            location[0] + x * scale, location[1] + y * scale, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    fun pull(x: Float, distance: Float, release: Boolean = true) {
        touch(MotionEvent.ACTION_DOWN, x, 25f)
        repeat(7) { index ->
            Thread.sleep(50)
            touch(MotionEvent.ACTION_MOVE, x, 25 + distance * (index + 1) / 7)
        }
        if (release) touch(MotionEvent.ACTION_UP, x, 25 + distance)
    }

    fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("WebView condition timed out")
    }

    fun close() {
        cancelTouch()
        instrumentation.runOnMainSync { activity.finish() }
        if (!wasInteractive) shell("input keyevent KEYCODE_SLEEP")
    }
}
