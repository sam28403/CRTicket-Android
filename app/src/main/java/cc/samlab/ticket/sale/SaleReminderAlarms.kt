package cc.samlab.ticket.sale

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import cc.samlab.ticket.ui.sale.saleZone
import java.time.Instant

internal object SaleReminderAlarms {
    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reschedule(context: Context, rows: List<SaleReminder> = SaleReminderStore(context).read()): Boolean {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val preferences = context.getSharedPreferences("sale-reminders", Context.MODE_PRIVATE)
        val previous = preferences.getStringSet("alarms", emptySet()).orEmpty()
        val groups = reminderGroups(rows)
        previous.mapNotNull { it.toLongOrNull() }.filterNot { it in groups }.forEach {
            alarm.cancel(pending(context, it))
        }
        if (!allowed(context)) return false
        return try {
            val now = System.currentTimeMillis()
            groups.keys.forEach { saleAt ->
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, maxOf(now + 1000, saleAt - 120_000), pending(context, saleAt),
                )
            }
            val end = Instant.ofEpochMilli(now).atZone(saleZone).toLocalDate()
                .atTime(18, 0).atZone(saleZone).toInstant().toEpochMilli()
            if (groups.isNotEmpty() && now < end) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, cleanup(context))
            } else alarm.cancel(cleanup(context))
            preferences.edit().putStringSet("alarms", groups.keys.map { it.toString() }.toSet()).apply()
            true
        } catch (_: SecurityException) { false }
    }

    fun cancelAll(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val preferences = context.getSharedPreferences("sale-reminders", Context.MODE_PRIVATE)
        preferences.getStringSet("alarms", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
            .forEach { alarm.cancel(pending(context, it)) }
        alarm.cancel(cleanup(context))
        preferences.edit().remove("alarms").apply()
    }

    private fun cleanup(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, SaleReminderReceiver::class.java)
            .setAction("cc.samlab.ticket.SALE_REMINDER_CLEAR")
            .setData(Uri.parse("crticket://sale-reminder/clear"))
            .putExtra("cleanup", true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pending(context: Context, saleAt: Long): PendingIntent {
        val intent = Intent(context, SaleReminderReceiver::class.java)
            .setAction("cc.samlab.ticket.SALE_REMINDER")
            .setData(Uri.parse("crticket://sale-reminder/$saleAt"))
            .putExtra("saleAt", saleAt)
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
