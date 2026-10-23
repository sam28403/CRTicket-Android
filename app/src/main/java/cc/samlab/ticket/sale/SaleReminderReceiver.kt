package cc.samlab.ticket.sale

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.notifications.NotificationAccess

class SaleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = SaleReminderStore(context)
        if (intent.getBooleanExtra("cleanup", false)) {
            store.clearAll()
            return
        }
        val saleAt = intent.getLongExtra("saleAt", 0)
        if (saleAt == 0L) {
            SaleReminderAlarms.reschedule(context)
            return
        }
        val now = System.currentTimeMillis()
        if (now < saleAt - 120_000 || now >= saleAt || store.wasDelivered(saleAt)) return
        val rows = store.read(now).filter { it.saleAt == saleAt }
        if (rows.isEmpty()) return
        createChannel(context)
        val target = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("crticket://sale-reminders/$saleAt"))
            .putExtra(OPEN_REMINDERS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 510, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stations = rows.joinToString(", ") { it.station }
        val body = appString(R.string.sale_reminder_body, rows.first().time.toString(), stations)
        val alarm = SaleReminderPreferences.mode(context) == SaleReminderMode.ALARM &&
            SaleReminderPreferences.alarmAccess(context)
        if (alarm) SaleAlarmNotification.createChannel(context)
        val channel = if (alarm && NotificationAccess.enabled(context, SaleAlarmNotification.CHANNEL))
            SaleAlarmNotification.CHANNEL else CHANNEL
        if (!NotificationAccess.enabled(context, channel)) return
        if (channel == SaleAlarmNotification.CHANNEL) {
            try {
                SaleAlarmService.start(context, saleAt, body)
                return
            } catch (error: IllegalStateException) {
                android.util.Log.w("SaleReminder", "Cannot start alarm playback", error)
            } catch (error: SecurityException) {
                android.util.Log.w("SaleReminder", "Alarm playback permission denied", error)
            }
        }
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_ticket)
            .setContentTitle(appString(R.string.sale_reminder_subscribe))
            .setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
        val notification = builder.build()
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify("sale-reminder-$saleAt", 510, notification)
            store.delivered(saleAt)
        } catch (_: SecurityException) { }
    }

    companion object {
        const val CHANNEL = "sale-time-reminders"
        const val OPEN_REMINDERS = "openSaleReminders"

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, appString(R.string.sale_reminder_subscribe), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
