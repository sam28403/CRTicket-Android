package cc.samlab.ticket.backup

import android.content.Context
import cc.samlab.ticket.R
import cc.samlab.ticket.arrival.ArrivalAccess
import cc.samlab.ticket.notifications.NotificationAccess
import cc.samlab.ticket.sale.*
import org.json.JSONObject

internal object BackupPermissionReview {
    fun preferences(context: Context) = context.getSharedPreferences("backup-permission-review", Context.MODE_PRIVATE)

    fun record(context: Context, value: JSONObject) {
        val preferences = preferences(context)
        val features = preferences.getStringSet("features", emptySet()).orEmpty().toMutableSet()
        if (value.optBoolean("arrivalEnabled")) features += "arrival"
        if (value.has("saleAlarmEnabled")) features += "sale"
        if (value.optJSONObject("monitorConfig") != null) features += "monitor"
        preferences.edit().putStringSet("features", features).commit()
    }

    fun missing(context: Context): List<Int> {
        val features = preferences(context).getStringSet("features", emptySet()).orEmpty()
        return buildList {
            if ("arrival" in features && (!ArrivalAccess.fine(context) || !ArrivalAccess.background(context) ||
                    !cc.samlab.ticket.arrival.ArrivalNotifications(context).enabled())) add(R.string.settings_arrival)
            if ("monitor" in features && !NotificationAccess.enabled(context, "ticket-monitor-running")) {
                add(R.string.home_monitor)
            }
            if ("sale" in features && (!SaleReminderAlarms.allowed(context) ||
                    !NotificationAccess.enabled(context, SaleReminderPreferences.channel(context)) ||
                    (SaleReminderPreferences.mode(context) == SaleReminderMode.ALARM &&
                        !SaleReminderPreferences.alarmAccess(context)))) add(R.string.settings_sale_reminders)
        }
    }

    fun dismiss(context: Context) { preferences(context).edit().clear().apply() }
}
