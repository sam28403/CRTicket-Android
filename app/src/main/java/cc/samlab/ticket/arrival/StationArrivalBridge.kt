package cc.samlab.ticket.arrival

import android.app.Activity
import android.app.NotificationManager
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.ui.ExpressiveDialogHost
import org.json.JSONObject

class StationArrivalBridge(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
    dialogs: ExpressiveDialogHost,
) {
    private val store = ArrivalStore(activity)
    private val monitor = StationArrivalMonitor.get(activity)
    private var pending: String? = null
    private var permissionRound = 0
    private var notificationPending = false
    private var activating = false
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
            else finish(false, "请开启系统通知和“到站提醒”通知渠道")
        }
    }, channelId = ArrivalNotifications.CHANNEL, channelName = "到站提醒",
        channelDescription = "到达附近车站时提醒，可点按打开车站大屏",
        channelImportance = NotificationManager.IMPORTANCE_HIGH,
        permissionRequest = 53, settingsRequest = 54, dialogs = dialogs,
        resumeFromSettings = true)

    fun state(id: String) = reply(id, status())

    private fun status(): JSONObject = JSONObject().put("success", true)
        .put("supported", ArrivalAccess.supported(activity))
        .put("enabled", store.enabled)
        .put("active", store.enabled && store.registered && ArrivalAccess.ready(activity))
        .put("fine", ArrivalAccess.fine(activity))
        .put("background", ArrivalAccess.background(activity))
        .put("locationEnabled", ArrivalAccess.locationEnabled(activity))
        .put("notifications", ArrivalNotifications(activity).enabled())
        .put("stations", ArrivalCatalog.stations(activity).size)
        .put("message", if (!ArrivalAccess.supported(activity))
            "此设备需要可用的 Google Play 服务才能使用低功耗到站提醒"
            else if (store.enabled && !ArrivalAccess.ready(activity))
                "到站提醒已暂停，请检查系统定位、精确定位、始终允许定位和通知权限"
            else store.message)

    fun enable(id: String) {
        if (pending != null || store.enableRequested || activating) {
            reply(id, JSONObject().put("success", false).put("message", "请先完成当前权限申请"))
            return
        }
        pending = id
        permissionRound++
        if (!ArrivalAccess.supported(activity)) {
            finish(false, "此设备需要可用的 Google Play 服务才能使用低功耗到站提醒")
        } else if (!ArrivalAccess.locationEnabled(activity)) {
            finish(false, "请先在手机系统设置中开启定位，再开启到站提醒")
        } else {
            permissions.request()
        }
    }

    private fun activate() {
        activating = true
        try {
            val round = permissionRound
            monitor.start {
                if (round == permissionRound) {
                    activating = false
                    finish(store.enabled, store.message)
                }
            }
        } catch (error: Exception) {
            activating = false
            finish(false, error.message ?: "无法开启到站提醒")
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
        finish(false, "已取消开启到站提醒")
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
        store.enableRequested = false
        val id = pending ?: return
        pending = null
        reply(id, status().put("success", success).put("message", message))
    }
    private fun reply(id: String, result: JSONObject) {
        callback("crticketNotificationResult", id, result)
    }
}
