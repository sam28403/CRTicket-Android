package cc.samlab.ticket

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.ResultReceiver
import android.os.SystemClock
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.tracking.TrainPhase
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MonitorLifecycleDeviceFixture(private val instrumentation: Instrumentation) {
    val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var activity: MainActivity? = null

    fun snapshot(): JSONObject = JSONObject()
        .put("preferences", MonitorPreferenceSnapshot.capture(context))
        .put("interactive", context.getSystemService(PowerManager::class.java).isInteractive)
        .put("trainNotification", notice(TrainTrackingNotifications.NOTIFICATION_ID) != null)

    fun open() {
        shell("input keyevent KEYCODE_WAKEUP")
        finish()
        activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity?.setShowWhenLocked(true)
            activity?.setTurnScreenOn(true)
        }
        instrumentation.waitForIdleSync()
        await("Activity did not regain foreground focus after wake-up", timeout = 20_000) {
            var ready = false
            onMain {
                val current = requireNotNull(activity)
                ready = current.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                    current.window.decorView.hasWindowFocus()
            }
            ready
        }
    }

    fun finish() {
        val current = activity ?: return
        instrumentation.runOnMainSync { current.finish() }
        instrumentation.waitForIdleSync()
        await("Activity was not destroyed") { current.isDestroyed }
        activity = null
    }

    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    fun navigationViewAlpha(): Float {
        var alpha = 1f
        onMain {
            alpha = requireNotNull(activity)
                .findViewById<android.view.View>(R.id.expressive_navigation_view).alpha
        }
        return alpha
    }

    fun commitBack() = onMain { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }

    fun evaluate(script: String): String {
        val completed = CountDownLatch(1)
        var result = "null"
        onMain {
            requireNotNull(activity).webView.evaluateJavascript(script) {
                result = it
                completed.countDown()
            }
        }
        check(completed.await(10, TimeUnit.SECONDS)) { "WebView script callback timed out" }
        return result
    }

    fun predictiveBack(
        progress: Float?,
        cancel: Boolean = false,
        edge: Int = androidx.activity.BackEventCompat.EDGE_LEFT,
    ) = onMain {
        val dispatcher = requireNotNull(activity).onBackPressedDispatcher
        val event = androidx.activity.BackEventCompat(0f, 300f, progress ?: 0f, edge)
        if (cancel) dispatcher.dispatchOnBackCancelled()
        else if (progress == null) dispatcher.dispatchOnBackStarted(event)
        else dispatcher.dispatchOnBackProgressed(event)
    }

    fun reload() = onMain { requireNotNull(activity).webView.reload() }

    fun startTrain(payload: String) {
        val completed = CountDownLatch(1)
        var error = "Train tracking did not start"
        var successful = false
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                successful = resultCode == 1
                error = resultData?.getString("message") ?: error
                completed.countDown()
            }
        }
        onMain {
            context.startForegroundService(Intent(context, TrainTrackingService::class.java)
                .putExtra(TrainTrackingService.PAYLOAD, payload)
                .putExtra(TrainTrackingService.RESULT, receiver))
        }
        check(completed.await(15, TimeUnit.SECONDS) && successful) { error }
    }

    fun trainState(): JSONObject {
        var state = JSONObject()
        onMain {
            TrainTrackingBridge(requireNotNull(activity)) { _, _, result -> state = result }
                .state("lifecycle-fixture")
        }
        return state
    }

    fun notice(id: Int): Notification? = manager.activeNotifications
        .firstOrNull { it.id == id }?.notification

    fun cancelTrainNotice() = manager.cancel(TrainTrackingNotifications.NOTIFICATION_ID)

    fun await(message: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError(message)
    }

    fun restore(snapshot: JSONObject) {
        // Reuse the foreground activity when possible; relaunching while an IME or
        // dialog is closing can time out on large-screen devices.
        if (activity == null || activity?.isDestroyed == true) open()
        onMain {
            TicketMonitorService.stop(context)
            TrainTrackingService.stop(context)
        }
        instrumentation.waitForIdleSync()
        // Restore complete stores, including the original session ID and deadline.
        MonitorPreferenceSnapshot.restore(context, snapshot.getJSONObject("preferences"))
        onMain {
            val payload = TrainTrackingStore.read(context)
            val session = TrainTrackingStore.session(context)
            if (payload != null && session?.expired(System.currentTimeMillis()) != true) {
                val timeline = TrainTrackingStore.parse(payload)
                if (timeline.status(System.currentTimeMillis()).phase != TrainPhase.ARRIVED) {
                    context.startForegroundService(Intent(context, TrainTrackingService::class.java))
                } else if (snapshot.optBoolean("trainNotification")) {
                    val notifications = TrainTrackingNotifications(context)
                    notifications.publish(notifications.build(timeline, System.currentTimeMillis(),
                        quietUpdate = true, session = session))
                }
            }
            TicketMonitorService.restore(context)
        }
        instrumentation.waitForIdleSync()
        finish()
        if (!snapshot.getBoolean("interactive")) shell("input keyevent KEYCODE_SLEEP")
    }

    fun withSnapshot(test: () -> Unit) {
        val original = snapshot()
        try {
            open()
            test()
        } finally {
            restore(original)
        }
    }
}
