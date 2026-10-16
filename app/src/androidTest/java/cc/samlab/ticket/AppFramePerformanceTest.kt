package cc.samlab.ticket

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.home.homeColorScheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real wall-clock animations; Compose's test clock must not fast-forward them. */
@RunWith(AndroidJUnit4::class)
class AppFramePerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test
    fun navigationAndScrollingFrameReport() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        val report = JSONObject()
        val recorder = FramePerformanceRecorder(activity.window)
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                recorder.attach()
            }
            await { evaluate("Boolean(window.crticketNavigate)") == "true" }
            Thread.sleep(1000)
            for (pass in 0..1) {
                for ((name, route) in listOf(
                    "settings" to "/settings", "about" to "/about", "history" to "/history",
                    "list" to "/list", "station" to "/station?station=北京南", "home" to "/home",
                    "web-ticket" to "/",
                )) {
                    recorder.reset()
                    evaluate("window.crticketNavigate(${JSONObject.quote(route)})")
                    Thread.sleep(1100)
                    report.put("${if (pass == 0) "first" else "warm"}-$name", recorder.summary())
                }
            }
            for ((name, route) in listOf(
                "home" to "/home", "settings" to "/settings", "list" to "/list",
                "station" to "/station?station=北京南", "web-ticket" to "/",
            )) {
                evaluate("window.crticketNavigate(${JSONObject.quote(route)})")
                Thread.sleep(1500)
                recorder.reset()
                repeat(4) {
                    swipeUp()
                    Thread.sleep(400)
                }
                Thread.sleep(700)
                report.put("scroll-$name", recorder.summary())
            }
            instrumentation.runOnMainSync {
                val start = System.nanoTime()
                repeat(200) { homeColorScheme(activity, dark = true, pureBlack = false) }
                report.put("theme200CallsMs", (System.nanoTime() - start) / 1e6)
            }
            report.put("model", android.os.Build.MODEL)
            report.put("debug", BuildConfig.DEBUG)
            val label = InstrumentationRegistry.getArguments().getString("label") ?: "run"
            val directory = File(activity.getExternalFilesDir(null), "performance").apply { mkdirs() }
            File(directory, "$label.json").writeText(report.toString(2))
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", report.toString() + "\n")
            })
        } finally {
            instrumentation.runOnMainSync {
                recorder.close()
                activity.finish()
            }
        }
    }

    private fun swipeUp() {
        val bounds = android.graphics.Rect()
        instrumentation.runOnMainSync { activity.window.decorView.getWindowVisibleDisplayFrame(bounds) }
        val x = bounds.centerX().toFloat()
        val start = bounds.top + bounds.height() * 0.78f
        val end = bounds.top + bounds.height() * 0.28f
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, start)
        repeat(12) { step ->
            Thread.sleep(16)
            touch(MotionEvent.ACTION_MOVE, start + (end - start) * (step + 1) / 12)
        }
        touch(MotionEvent.ACTION_UP, end)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Web router did not become ready")
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                result = it
                latch.countDown()
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return result
    }
}
