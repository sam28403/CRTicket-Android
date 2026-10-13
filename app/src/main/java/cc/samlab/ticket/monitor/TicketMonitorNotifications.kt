package cc.samlab.ticket.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.notifications.MonitorNotifications
import cc.samlab.ticket.notifications.NotificationAccess

class TicketMonitorNotifications(private val context: Context) {
    companion object {
        const val ID = 3
        private const val CHANNEL = "ticket-monitor-running"
        private const val AVAILABILITY = "ticket-availability"
    }
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "余票后台监控", NotificationManager.IMPORTANCE_LOW,
        ))
        manager.createNotificationChannel(NotificationChannel(
            AVAILABILITY, "余票提醒", NotificationManager.IMPORTANCE_HIGH,
        ))
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        context, ID,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MonitorNotifications.OPEN_MONITOR, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ongoing(config: TicketMonitorConfig, error: String): Notification {
        val stop = PendingIntent.getBroadcast(
            context, ID, Intent(context, StopTicketMonitorReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_ticket)
            .setContentTitle("余票监控：${config.fromName} → ${config.toName}")
            .setContentText(if (error.isEmpty()) "${config.date} · 每 ${config.interval} 秒查询"
                else "查询暂未成功，稍后自动重试")
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "停止监控", stop).build())
            .build()
    }

    fun update(config: TicketMonitorConfig, error: String) {
        runCatching { manager.notify(ID, ongoing(config, error)) }
    }

    fun available(details: List<String>) {
        if (details.isEmpty() || !NotificationAccess.enabled(context, AVAILABILITY)) return
        val body = details.take(3).joinToString("\n")
        val notification = Notification.Builder(context, AVAILABILITY)
            .setSmallIcon(R.drawable.ic_notification_ticket)
            .setContentTitle("余票提醒：${details.size} 趟车有票或余票变化")
            .setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open()).setAutoCancel(true).build()
        runCatching { manager.notify(1, notification) }
    }
}
