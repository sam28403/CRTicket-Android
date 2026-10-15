package cc.samlab.ticket.arrival

import android.content.Context
import cc.samlab.ticket.R

class ArrivalStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    var enableRequested: Boolean
        get() = preferences.getBoolean("enableRequested", false)
        set(value) { preferences.edit().putBoolean("enableRequested", value).apply() }
    var message: String
        get() = preferences.getString("message", context.getString(R.string.arrival_not_enabled)).orEmpty()
        set(value) { preferences.edit().putString("message", value).apply() }
    var registered: Boolean
        get() = preferences.getBoolean("registered", false)
        set(value) { preferences.edit().putBoolean("registered", value).apply() }

    fun lastNotification(name: String): Long = preferences.getLong("notified:$name", 0)
    fun notified(name: String, now: Long) {
        preferences.edit().putLong("notified:$name", now).apply()
    }
}
