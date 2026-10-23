package cc.samlab.ticket.sale

import android.app.Notification
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString

internal object SaleAlarmNotification {
    const val CHANNEL = "sale-time-alarm-playback"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL, appString(R.string.sale_mode_alarm),
            manager.getNotificationChannel("sale-time-alarms")?.importance ?: NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // Audio is managed by the alarm service; never play a second notification sound.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(manager.isNotificationPolicyAccessGranted)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun configure(context: Context, builder: Notification.Builder, saleAt: Long, body: String) {
        val target = Intent(context, SaleAlarmActivity::class.java)
            .setData(Uri.parse("crticket://sale-alarm/$saleAt"))
            .putExtra("saleAt", saleAt).putExtra("body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 36) {
                setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            } else if (Build.VERSION.SDK_INT >= 35) {
                @Suppress("DEPRECATION")
                setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }
        val pending = PendingIntent.getActivity(context, 511, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle())
        val dismiss = PendingIntent.getBroadcast(context, 512,
            Intent(context, SaleAlarmDismissReceiver::class.java).setData(target.data)
                .putExtra("saleAt", saleAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true).setContentIntent(pending)
            .setDeleteIntent(dismiss)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setTimeoutAfter(60_000)
            .addAction(Notification.Action.Builder(null,
                appString(R.string.sale_alarm_dismiss), dismiss).build())
    }

    fun build(context: Context, saleAt: Long, body: String): Notification {
        createChannel(context)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_ticket)
            .setContentTitle(appString(R.string.settings_sale_reminders))
            .setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setOngoing(true).addExtras(android.os.Bundle().apply { putLong("saleAt", saleAt) })
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        configure(context, builder, saleAt, body)
        return builder.build()
    }
}
