package cc.samlab.ticket.arrival

import android.app.Activity
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import cc.samlab.ticket.R
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.ui.ExpressiveDialogHost
import org.json.JSONObject

class StationArrivalBridge(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
    dialogs: ExpressiveDialogHost,
    private val activationTimeoutMillis: Long = 25_000L,
) {
    private val store = ArrivalStore(activity)
    private val monitor = StationArrivalMonitor.get(activity)
    private var pending: String? = null
    private var permissionRound = 0
    private var notificationPending = false
    private var activating = false
    private val handler = Handler(Looper.getMainLooper())
    private val activationTimeout = Runnable {
        if (activating) finish(false, activity.getString(R.string.arrival_enable_failed))
    }
    private val permissions = ArrivalPermissions(
        activity,
        dialogs,
        accepted = { store.enableRequested = true },
    ) { granted, message ->
        if (pending != null || store.enableRequested) {
            if (granted) requestNotifications()
            else finish(false, message)
        }
    }
    private val notifications = MonitorNotifications(activity, { _, id, result ->
        if (notificationPending && id == "arrival-permission:$permissionRound") {
            notificationPending = false
            if (result.optBoolean("granted")) activate()
            else finish(false, activity.getString(R.string.arrival_notifications_required))
        }
    }, channelId = ArrivalNotifications.CHANNEL, channelName = activity.getString(R.string.arrival_channel),
        channelDescription = activity.getString(R.string.arrival_channel_description),
        channelImportance = NotificationManager.IMPORTANCE_HIGH,
        permissionRequest = 53, settingsRequest = 54, dialogs = dialogs,
        resumeFromSettings = true)

    fun state(id: String) = reply(id, status())

    internal fun status(): JSONObject = JSONObject().put("success", true)
        .put("supported", ArrivalAccess.supported(activity))
        .put("enabled", store.enabled && store.registered && !activating)
        .put("starting", activating)
        .put("active", store.enabled && store.registered && ArrivalAccess.ready(activity))
        .put("fine", ArrivalAccess.fine(activity))
        .put("background", ArrivalAccess.background(activity))
        .put("locationEnabled", ArrivalAccess.locationEnabled(activity))
        .put("notifications", ArrivalNotifications(activity).enabled())
        .put("stations", ArrivalCatalog.stations(activity).size)
        .put("message", if (!ArrivalAccess.supported(activity))
            activity.getString(R.string.arrival_services_required)
            else if (store.enabled && !ArrivalAccess.ready(activity))
                activity.getString(R.string.arrival_access_paused)
            else store.message)

    fun enable(id: String) {
        if (!ArrivalAccess.supported(activity)) {
            val message = activity.getString(R.string.arrival_google_services_required)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            reply(id, status().put("success", false).put("message", message))
            return
        }
        if (pending != null || store.enableRequested || activating) {
            reply(id, status())
            return
        }
        pending = id
        permissionRound++
        if (!ArrivalAccess.supported(activity)) {
            finish(false, activity.getString(R.string.arrival_services_required))
        } else if (!ArrivalAccess.locationEnabled(activity)) {
            finish(false, activity.getString(R.string.arrival_location_disabled))
        } else {
            permissions.request()
        }
    }

    private fun activate() {
        activating = true
        reply("native-settings", status())
        handler.removeCallbacks(activationTimeout)
        handler.postDelayed(activationTimeout, activationTimeoutMillis)
        try {
            val round = permissionRound
            monitor.start {
                if (round == permissionRound) {
                    finish(store.enabled && store.registered && ArrivalAccess.ready(activity), store.message)
                }
            }
        } catch (error: Exception) {
            activating = false
            finish(false, error.message ?: activity.getString(R.string.arrival_enable_failed))
        }
    }

    private fun requestNotifications() {
        notificationPending = true
        notifications.requestPermission("arrival-permission:$permissionRound")
    }

    fun disable(id: String) {
        permissionRound++
        notificationPending = false
        activating = false
        permissions.cancel()
        notifications.cancelPermissionRequest()
        if (pending != null || store.enableRequested) {
            finish(false, activity.getString(R.string.arrival_cancelled))
        }
        handler.removeCallbacks(activationTimeout)
        monitor.stop()
        reply(id, status())
    }

    fun onRequestPermissionsResult(code: Int, results: IntArray) {
        permissions.onRequestPermissionsResult(code)
        notifications.onRequestPermissionsResult(code, results)
    }
    fun onActivityResult(code: Int) {
        notifications.onActivityResult(code)
    }

    fun onPause() {
        permissions.onPause()
        notifications.onPause()
    }

    fun close() {
        // 销毁旧 Activity 时丢弃旧回调，保留已经确认的开启意图给新实例。
        permissionRound++
        handler.removeCallbacks(activationTimeout)
        pending = null
        notificationPending = false
        permissions.cancel()
        notifications.cancelPermissionRequest()
    }

    fun onResume() {
        permissions.onResume()
        notifications.onResume()
        if (permissions.inProgress || notificationPending || activating) return
        if (store.enableRequested) {
            // 系统授权导致 Activity / 进程重建后，继续用户已确认的开启流程。
            permissionRound++
            permissions.resume()
        } else {
            monitor.restore()
        }
    }

    private fun finish(success: Boolean, message: String) {
        handler.removeCallbacks(activationTimeout)
        val id = pending ?: "native-settings"
        pending = null
        activating = false
        notificationPending = false
        permissionRound++
        store.enableRequested = false
        if (!success) {
            permissions.cancel()
            notifications.cancelPermissionRequest()
            monitor.stop()
        }
        store.message = message
        reply(id, status().put("success", success).put("message", message))
        if (!success) Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
    private fun reply(id: String, result: JSONObject) {
        callback("crticketNotificationResult", id, result)
    }
}
