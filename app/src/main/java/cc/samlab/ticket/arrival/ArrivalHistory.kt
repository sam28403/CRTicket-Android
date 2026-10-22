package cc.samlab.ticket.arrival

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StationVisit(
    val station: String,
    val firstArrival: Long,
    val historicalReminder: Boolean = false,
)

/** Independent of the notification cooldown: revisits never replace the first arrival. */
class ArrivalHistory(context: Context) {
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)

    fun visits(): List<StationVisit> = synchronized(lock) { read() }

    fun record(station: String, time: Long) = synchronized(lock) {
        val visits = read()
        if (visits.none { it.station == station }) {
            save(visits + StationVisit(station, time))
        }
    }

    private fun read(): List<StationVisit> {
        val saved = preferences.getString("firstVisits", null)
        if (saved != null) {
            val rows = JSONArray(saved)
            return List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                StationVisit(
                    row.getString("station"), row.getLong("time"),
                    row.optBoolean("historical"),
                )
            }.sortedByDescending { it.firstArrival }
        }
        // Older versions retained only the most recent notification per station.
        val legacy = preferences.all.mapNotNull { (key, value) ->
            if (key.startsWith("notified:") && value is Long && value > 0) {
                StationVisit(key.removePrefix("notified:"), value, historicalReminder = true)
            } else null
        }.sortedByDescending { it.firstArrival }
        save(legacy)
        return legacy
    }

    private fun save(visits: List<StationVisit>) {
        val rows = JSONArray()
        visits.forEach {
            rows.put(JSONObject().put("station", it.station).put("time", it.firstArrival)
                .put("historical", it.historicalReminder))
        }
        preferences.edit().putString("firstVisits", rows.toString()).apply()
    }

    internal companion object {
        val lock = Any()
    }
}
