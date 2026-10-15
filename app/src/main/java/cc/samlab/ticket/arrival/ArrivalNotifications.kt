package cc.samlab.ticket.arrival

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.notifications.NotificationAccess

class ArrivalNotifications(private val context: Context) {
    companion object {
        const val CHANNEL = "station-arrival"
        const val OPEN_STATION = "openArrivalStation"
    }
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.arrival_channel),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.arrival_channel_description)
        })
    }

    fun enabled(): Boolean = NotificationAccess.enabled(context, CHANNEL)

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
            .setContentTitle(context.getString(R.string.arrival_notification_title, station.removeSuffix("站")))
            .setContentText(context.getString(R.string.arrival_notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .build()
    }
}
