package cc.samlab.ticket

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import cc.samlab.ticket.tracking.TrainPhase
import cc.samlab.ticket.tracking.TrainTrackingBridge
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TrainTrackingDeviceFixture(private val instrumentation: Instrumentation) {
    private val context = instrumentation.targetContext
    private val original = TrainTrackingStore.read(context)
    private val originalSession = TrainTrackingStore.session(context)
    private val wasInteractive = context.getSystemService(android.os.PowerManager::class.java).isInteractive
    private lateinit var activity: MainActivity

    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    fun open() {
        shell("input keyevent KEYCODE_WAKEUP")
        activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        instrumentation.waitForIdleSync()
    }

    fun start(payload: String) {
        val done = CountDownLatch(1)
        var successful = false
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                successful = resultCode == 1
                done.countDown()
            }
        }
        instrumentation.runOnMainSync {
            context.startForegroundService(Intent(context, TrainTrackingService::class.java)
                .putExtra(TrainTrackingService.PAYLOAD, payload)
                .putExtra(TrainTrackingService.RESULT, receiver))
        }
        check(done.await(15, TimeUnit.SECONDS) && successful) { "Test tracking did not start" }
    }

    fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) {
                result = it
                done.countDown()
            }
        }
        check(done.await(15, TimeUnit.SECONDS)) { "WebView debug callback timed out" }
        return result
    }

    fun state(): JSONObject {
        var result = JSONObject()
        instrumentation.runOnMainSync {
            TrainTrackingBridge(activity) { _, _, data -> result = data }.state("fixture")
        }
        return result
    }

    fun countdownPayload(): String {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).withSecond(0).withNano(0)
        val start = now.minusHours(1)
        val arrival = now.plusMinutes(16)
        val end = now.plusMinutes(45)
        val base = start.toLocalDate()
        fun clock(value: ZonedDateTime) = value.toLocalTime().toString()
        fun day(value: ZonedDateTime) = ChronoUnit.DAYS.between(base, value.toLocalDate())
        return JSONObject().put("train", "G97/G100").put("date", base.toString())
            .put("queryTrain", "G97").put("stops", JSONArray()
                .put(JSONObject().put("station", "调试起点").put("train", "G97")
                    .put("departure", clock(start)).put("departureDay", day(start)))
                .put(JSONObject().put("station", "调试换号站").put("train", "G100")
                    .put("arrival", clock(arrival)).put("arrivalDay", day(arrival))
                    .put("departure", clock(arrival.plusMinutes(2))).put("departureDay", day(arrival.plusMinutes(2))))
                .put(JSONObject().put("station", "调试终点").put("train", "G100")
                    .put("arrival", clock(end)).put("arrivalDay", day(end))))
            .toString()
    }

    fun restore() {
        shell("input keyevent KEYCODE_WAKEUP")
        if (::activity.isInitialized) {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
        }
        TrainTrackingService.stop(context)
        if (original != null && originalSession?.expired(System.currentTimeMillis()) != true) {
            val timeline = TrainTrackingStore.parse(original)
            if (timeline.status(System.currentTimeMillis()).phase == TrainPhase.ARRIVED) {
                TrainTrackingStore.save(context, original, originalSession)
                val notifications = TrainTrackingNotifications(context)
                notifications.publish(notifications.build(timeline, System.currentTimeMillis(),
                    session = originalSession))
            } else {
                start(original)
            }
        }
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
        if (!wasInteractive) shell("input keyevent KEYCODE_SLEEP")
    }
}
