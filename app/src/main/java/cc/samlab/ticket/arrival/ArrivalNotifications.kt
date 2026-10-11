package cc.samlab.ticket.arrival

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R

class ArrivalNotifications(private val context: Context) {
    companion object {
        const val CHANNEL = "station-arrival"
        const val OPEN_STATION = "openArrivalStation"
    }
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "到站提醒",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "到达附近车站时提醒，可点按打开车站大屏"
        })
    }

    fun enabled(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun publish(station: String): Boolean {
        createChannel()
        if (!enabled()) return false
        return try {
            manager.notify("station-arrival", 401, build(station))
            true
        } catch (_: SecurityException) { false }
    }

    fun build(station: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("crticket://arrival/${Uri.encode(station)}"))
            .putExtra(OPEN_STATION, station)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 401, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_ticket)
            .setContentTitle("欢迎到访${station.removeSuffix("站")}站")
            .setContentText("点按查看当前车站大屏")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .build()
    }
}
