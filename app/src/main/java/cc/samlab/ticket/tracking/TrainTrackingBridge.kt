package cc.samlab.ticket.tracking

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import cc.samlab.ticket.notifications.MonitorNotifications
import org.json.JSONObject

class TrainTrackingBridge(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
) {
    private val notifications = TrainTrackingNotifications(activity)
    private val permission = MonitorNotifications(
        activity, callback,
        channelId = TrainTrackingNotifications.CHANNEL_ID,
        channelName = "列车追踪",
        channelDescription = "列车发车、到站和终到状态",
        channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
        permissionRequest = 43,
        settingsRequest = 44,
    )

    fun requestPermission(id: String) = permission.requestPermission(id)

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) =
        permission.onRequestPermissionsResult(requestCode, grantResults)

    fun onActivityResult(requestCode: Int) = permission.onActivityResult(requestCode)

    fun state(id: String) {
        val now = System.currentTimeMillis()
        val payload = TrainTrackingStore.read(activity)
        val current = payload?.let {
            runCatching { TrainTrackingStore.parse(payload) }.getOrNull()
        }
        var session = TrainTrackingStore.session(activity)
        if (current != null && session == null && notifications.isActive()) {
            val restored = TrainTrackingSession.restoreLegacy(current.end)
            session = restored
            TrainTrackingStore.save(activity, payload, restored)
            if (!restored.expired(now) && current.status(now).phase == TrainPhase.ARRIVED) {
                notifications.publish(notifications.build(current, now,
                    quietUpdate = true, session = restored))
            }
        }
        if (session?.expired(now) == true) TrainTrackingService.stop(activity)
        val active = current != null && session?.expired(now) != true &&
            notifications.enabled() && notifications.isActive()
        reply(id, JSONObject().put("success", true).put("active", active).apply {
            if (active) {
                put("train", current.train)
                put("date", current.date)
                put("queryTrain", current.queryTrain)
                put("currentTrain", current.currentTrain(now))
                put("finished", current.status(now).phase == TrainPhase.ARRIVED)
                session?.let { put("expiresAt", it.expiresAt) }
            }
        })
    }

    fun start(id: String, payload: String) {
        try {
            val timeline = TrainTrackingStore.parse(payload)
            require(notifications.enabled()) { "请先开启列车追踪通知权限" }
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    reply(id, JSONObject().put("success", resultCode == 1)
                        .put("finished", resultData?.getBoolean("finished", false) == true)
                        .put("message", resultData?.getString("message") ?: "已开启列车追踪"))
                }
            }
            activity.startForegroundService(
                Intent(activity, TrainTrackingService::class.java)
                    .putExtra(TrainTrackingService.PAYLOAD, payload)
                    .putExtra(TrainTrackingService.RESULT, receiver)
            )
        } catch (error: Exception) {
            reply(id, JSONObject().put("success", false)
                .put("message", error.message ?: "无法启动列车追踪"))
        }
    }

    fun stop(id: String) {
        TrainTrackingService.stop(activity)
        reply(id, JSONObject().put("success", true))
    }

    private fun reply(id: String, result: JSONObject) {
        callback("crticketNotificationResult", id, result)
    }
}
