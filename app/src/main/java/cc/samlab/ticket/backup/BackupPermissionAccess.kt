package cc.samlab.ticket.backup

import android.content.Context
import cc.samlab.ticket.arrival.ArrivalAccess
import cc.samlab.ticket.notifications.NotificationAccess
import cc.samlab.ticket.sale.SaleAlarmNotification
import cc.samlab.ticket.sale.SaleReminderAlarms
import cc.samlab.ticket.sale.SaleReminderPreferences
import org.json.JSONObject

/** Permission-dependent switches are restored only when this device can use them. */
internal data class BackupPermissionAccess(val arrival: Boolean, val saleAlarm: Boolean) {
    fun filter(requested: JSONObject): JSONObject = JSONObject(requested.toString()).apply {
        if (has("arrivalEnabled")) put("arrivalEnabled", getBoolean("arrivalEnabled") && arrival)
        if (has("saleAlarmEnabled")) put("saleAlarmEnabled", getBoolean("saleAlarmEnabled") && saleAlarm)
    }

    companion object {
        fun read(context: Context) = BackupPermissionAccess(
            arrival = ArrivalAccess.ready(context),
            saleAlarm = SaleReminderAlarms.allowed(context) &&
                SaleReminderPreferences.alarmAccess(context) &&
                NotificationAccess.enabled(context, SaleAlarmNotification.CHANNEL),
        )
    }
}
