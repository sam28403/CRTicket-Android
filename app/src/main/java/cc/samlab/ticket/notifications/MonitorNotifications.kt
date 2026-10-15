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
import cc.samlab.ticket.permissions.SettingsReturn
import cc.samlab.ticket.ui.ExpressiveDialogHost
import org.json.JSONObject

class MonitorNotifications(
    private val activity: Activity,
    private val callback: (String, String, JSONObject) -> Unit,
    private val channelId: String = CHANNEL_ID,
    private val channelName: String = activity.getString(R.string.notifications_ticket_channel),
    private val channelDescription: String = activity.getString(R.string.notifications_ticket_description),
    private val channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
    private val permissionRequest: Int = PERMISSION_REQUEST,
    private val settingsRequest: Int = SETTINGS_REQUEST,
    private val dialogs: ExpressiveDialogHost? = null,
    private val resumeFromSettings: Boolean = false,
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
    private val settingsReturn = SettingsReturn()

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, channelImportance).apply {
                description = channelDescription
            }
        )
    }

    fun enabled(): Boolean = NotificationAccess.enabled(activity, channelId)

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

        val openSettings = {
            val channelBlocked = manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
            val intent = Intent(
                if (channelBlocked) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                else Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            if (channelBlocked) intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            try {
                settingsReturn.launch()
                activity.startActivityForResult(intent, settingsRequest)
            } catch (_: Exception) {
                finishPermission(activity.getString(R.string.notifications_settings_failed))
            }
        }
        val title = activity.getString(R.string.notifications_enable_channel, channelName)
        val message = activity.getString(R.string.notifications_channel_settings, channelName)
        if (dialogs != null) {
            dialogs.show(
                title = title,
                message = message,
                confirmLabel = activity.getString(R.string.action_open_settings),
                onConfirm = openSettings,
                onDecline = { finishPermission() },
            )
        } else {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(activity.getString(R.string.action_open_settings)) { _, _ -> openSettings() }
                .setNegativeButton(activity.getString(R.string.action_not_now)) { _, _ -> finishPermission() }
                .setOnCancelListener { finishPermission() }
                .show()
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != permissionRequest) return
        // Dismissing the dialog leaves permission unchanged and allows another request.
        if (grantResults.isNotEmpty()) {
            preferences.edit().putBoolean("requested", true).apply()
        }
        finishPermission()
    }

    fun cancelPermissionRequest() {
        pending.clear()
        settingsReturn.cancel()
        dialogs?.dismiss()
    }

    fun onActivityResult(requestCode: Int) {
        if (requestCode == settingsRequest && !resumeFromSettings) finishPermission()
    }

    fun onPause() = settingsReturn.onPause()

    fun onResume() {
        if (settingsReturn.onResume() && pending.isNotEmpty()) finishPermission()
    }

    private fun finishPermission(message: String? = null) {
        settingsReturn.cancel()
        val granted = enabled()
        val statusMessage = message ?: if (granted) {
            activity.getString(R.string.notifications_enabled)
        } else {
            activity.getString(R.string.notifications_channel_disabled, channelName)
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
                .put("message", activity.getString(R.string.notifications_delivery_failed)),
        )
    }
}
