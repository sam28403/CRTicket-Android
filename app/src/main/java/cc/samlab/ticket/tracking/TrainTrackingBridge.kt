package cc.samlab.ticket.tracking

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.notifications.MonitorNotifications
import org.json.JSONObject

class TrainTrackingBridge(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
) {
    private val notifications = TrainTrackingNotifications(activity)
    private val recovery = TrainTrackingRecovery(activity, notifications)
    private val permission = MonitorNotifications(
        activity, callback,
        channelId = TrainTrackingNotifications.CHANNEL_ID,
        channelName = appString(R.string.tracking_channel_name),
        channelDescription = appString(R.string.tracking_channel_description),
        channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
        permissionRequest = 43,
        settingsRequest = 44,
    )

    fun requestPermission(id: String) = permission.requestPermission(id)

    fun queryDelay(id: String, payload: String) {
        Thread {
            val result = runCatching {
                JSONObject().put("success", true).put("result", TrainDelayRepository.query(payload))
            }.getOrElse { JSONObject().put("success", false) }
            reply(id, result)
        }.start()
    }

    fun queryStationDelay(id: String, payload: String) {
        Thread {
            val result = runCatching {
                JSONObject().put("success", true).put("rows", StationDelayRepository.query(payload))
            }.getOrElse { JSONObject().put("success", false) }
            reply(id, result)
        }.start()
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) =
        permission.onRequestPermissionsResult(requestCode, grantResults)

    fun onActivityResult(requestCode: Int) = permission.onActivityResult(requestCode)

    fun restore() {
        recovery.restore()
    }

    fun state(id: String) {
        val resumed = recovery.restore()
        val now = System.currentTimeMillis()
        val saved = recovery.read(now)
        val active = saved != null && notifications.enabled() &&
            (resumed || TrainTrackingService.isRunning(saved.session.id) || notifications.isActive())
        reply(id, JSONObject().put("success", true).put("active", active).apply {
            if (active && saved != null) {
                val current = saved.timeline
                put("train", current.train)
                put("date", current.date)
                put("queryTrain", current.queryTrain)
                put("currentTrain", current.currentTrain(now))
                put("finished", current.status(now).phase == TrainPhase.ARRIVED)
                put("expiresAt", saved.session.expiresAt)
            }
        })
    }

    fun start(id: String, payload: String) {
        try {
            TrainTrackingStore.parse(payload)
            require(notifications.enabled()) { appString(R.string.tracking_error_permission) }
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    reply(id, JSONObject().put("success", resultCode == 1)
                        .put("finished", resultData?.getBoolean("finished", false) == true)
                        .put("message", resultData?.getString("message") ?: appString(R.string.tracking_started)))
                }
            }
            activity.startForegroundService(
                Intent(activity, TrainTrackingService::class.java)
                    .putExtra(TrainTrackingService.PAYLOAD, payload)
                    .putExtra(TrainTrackingService.RESULT, receiver)
            )
        } catch (error: Exception) {
            reply(id, JSONObject().put("success", false)
                .put("message", error.message ?: appString(R.string.tracking_error_start)))
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
