package cc.samlab.ticket.notifications

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import org.json.JSONObject

class MonitorNotifications(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
    private val channelId: String = CHANNEL_ID,
    private val channelName: String = "余票提醒",
    private val channelDescription: String = "监测发现有票或余票数量变化时提醒",
    private val channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
    private val permissionRequest: Int = PERMISSION_REQUEST,
    private val settingsRequest: Int = SETTINGS_REQUEST,
) {
    companion object {
        private const val CHANNEL_ID = "ticket-availability"
        private const val NOTIFICATION_ID = 1
        private const val PERMISSION_REQUEST = 41
        private const val SETTINGS_REQUEST = 42
        const val OPEN_MONITOR = "openMonitor"
    }

    private val manager = activity.getSystemService(NotificationManager::class.java)
    private val preferences = activity.getSharedPreferences("notifications", Activity.MODE_PRIVATE)
    private val pending = mutableListOf<String>()

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, channelImportance).apply {
                description = channelDescription
            }
        )
    }

    fun enabled(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    // Called on the main thread; multiple callers share the same system prompt.
    fun requestPermission(id: String) {
        pending.add(id)
        if (pending.size > 1) return
        createChannel()
        if (enabled()) {
            finishPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            (!preferences.getBoolean("requested", false) ||
                activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                permissionRequest,
            )
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("开启$channelName")
            .setMessage("系统通知已关闭，请在设置中允许 CRTicket 通知并开启“$channelName”。")
            .setPositiveButton("去设置") { _, _ ->
                val channelBlocked = manager.areNotificationsEnabled() &&
                    manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
                val intent = Intent(
                    if (channelBlocked) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                if (channelBlocked) intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                try {
                    activity.startActivityForResult(intent, settingsRequest)
                } catch (_: Exception) {
                    finishPermission("无法打开通知设置，请在系统设置中手动开启")
                }
            }
            .setNegativeButton("暂不开启") { _, _ -> finishPermission() }
            .setOnCancelListener { finishPermission() }
            .show()
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != permissionRequest) return
        // Dismissing the dialog leaves permission unchanged and allows another request.
        if (grantResults.isNotEmpty()) {
            preferences.edit().putBoolean("requested", true).apply()
        }
        finishPermission()
    }

    fun onActivityResult(requestCode: Int) {
        if (requestCode == settingsRequest) finishPermission()
    }

    private fun finishPermission(message: String? = null) {
        val granted = enabled()
        val statusMessage = message ?: if (granted) {
            "系统通知已开启"
        } else {
            "未开启系统通知，请在系统设置中开启“$channelName”"
        }
        val result = JSONObject()
            .put("granted", granted)
            .put("message", statusMessage)
        val requests = pending.toList()
        pending.clear()
        requests.forEach { callback("crticketNotificationResult", it, result) }
    }

    fun publish(id: String, title: String, body: String) {
        createChannel()
        var success = false
        if (enabled()) {
            val intent = Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_MONITOR, true)
            val contentIntent = PendingIntent.getActivity(
                activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_ticket)
                .setContentTitle(title.take(200))
                .setContentText(body.take(2000))
                .setStyle(Notification.BigTextStyle().bigText(body.take(2000)))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(NOTIFICATION_ID, notification)
                success = true
            } catch (_: SecurityException) {
                // Permission can be revoked between the check and delivery.
            }
        }
        callback(
            "crticketNotificationResult", id,
            JSONObject().put("success", success)
                .put("message", "系统通知未发送，请检查通知权限并查看页面内的有票提醒"),
        )
    }
}
