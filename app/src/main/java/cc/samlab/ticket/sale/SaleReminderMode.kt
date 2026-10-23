package cc.samlab.ticket.sale

import android.app.NotificationManager
import android.content.Context
import android.os.Build

enum class SaleReminderMode { NOTIFICATION, ALARM }

object SaleReminderPreferences {
    fun channel(context: Context): String =
        if (mode(context) == SaleReminderMode.ALARM && alarmAccess(context))
            SaleAlarmNotification.CHANNEL else SaleReminderReceiver.CHANNEL

    fun mode(context: Context): SaleReminderMode =
        if (context.getSharedPreferences("sale-reminder-settings", Context.MODE_PRIVATE)
                .getBoolean("alarm", false)) SaleReminderMode.ALARM else SaleReminderMode.NOTIFICATION

    fun setMode(context: Context, mode: SaleReminderMode) {
        context.getSharedPreferences("sale-reminder-settings", Context.MODE_PRIVATE)
            .edit().putBoolean("alarm", mode == SaleReminderMode.ALARM).apply()
    }

    fun alarmAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.isNotificationPolicyAccessGranted &&
            (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent())
    }
}
