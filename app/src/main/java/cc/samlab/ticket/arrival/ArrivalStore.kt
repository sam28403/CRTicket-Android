package cc.samlab.ticket.arrival

import android.content.Context

class ArrivalStore(context: Context) {
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    var message: String
        get() = preferences.getString("message", "尚未开启到站提醒").orEmpty()
        set(value) { preferences.edit().putString("message", value).apply() }
    var registered: Boolean
        get() = preferences.getBoolean("registered", false)
        set(value) { preferences.edit().putBoolean("registered", value).apply() }

    fun lastNotification(name: String): Long = preferences.getLong("notified:$name", 0)
    fun notified(name: String, now: Long) {
        preferences.edit().putLong("notified:$name", now).apply()
    }
}
