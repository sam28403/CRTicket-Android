package cc.samlab.ticket.backup

import android.content.Context
import android.content.SharedPreferences
import cc.samlab.ticket.arrival.ArrivalHistory
import cc.samlab.ticket.arrival.StationVisit
import cc.samlab.ticket.arrival.mergeStationVisits
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.ui.railfan.VehicleSearch
import cc.samlab.ticket.ui.railfan.VehicleSearchHistory
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_BACKUP_NATIVE_LENGTH = 20 * 1024 * 1024

internal class BackupRailfanData(private val context: Context) {
    fun snapshot(value: JSONObject): JSONObject = value
        .put("railfanToolsEnabled", RailfanPreferences.enabled(context))
        .put("stationVisits", encodeVisits(ArrivalHistory(context).visits()))
        .put("vehicleSearchHistory", JSONArray(VehicleSearchHistory(context).read()))

    fun validate(value: JSONObject) {
        if (value.has("railfanToolsEnabled")) {
            require(value.get("railfanToolsEnabled") is Boolean) { "Invalid railfan preference" }
        }
        if (value.has("stationVisits")) {
            val rows = value.getJSONArray("stationVisits")
            require(rows.length() <= 10_000) { "Too many station visits" }
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val station = row.get("station")
                val time = row.get("time")
                require(station is String && station.isNotBlank() && station.length <= 100 &&
                    station.none { it.isISOControl() }) { "Invalid visited station" }
                require(time is Number && time.toDouble().isFinite() &&
                    time.toDouble() in 1.0..9_007_199_254_740_991.0 &&
                    time.toDouble() % 1.0 == 0.0) { "Invalid station visit time" }
                require(!row.has("historical") || row.get("historical") is Boolean) {
                    "Invalid historical visit flag"
                }
            }
        }
        if (value.has("vehicleSearchHistory")) {
            val rows = value.getJSONArray("vehicleSearchHistory")
            require(rows.length() <= 10) { "Too many vehicle searches" }
            for (index in 0 until rows.length()) {
                val keyword = rows.get(index)
                require(keyword is String && keyword.length <= 100 &&
                    VehicleSearch.parse(keyword) != null) { "Invalid vehicle search history" }
            }
        }
    }

    fun changes(value: JSONObject): Map<SharedPreferences, Map<String, Any?>> = buildMap {
        if (value.has("railfanToolsEnabled")) {
            put(context.getSharedPreferences("android", Context.MODE_PRIVATE),
                mapOf("railfanToolsEnabled" to value.getBoolean("railfanToolsEnabled")))
        }
        if (value.has("stationVisits")) {
            val rows = value.getJSONArray("stationVisits")
            val imported = List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                StationVisit(row.getString("station"), row.getLong("time"), row.optBoolean("historical"))
            }
            val merged = mergeStationVisits(ArrivalHistory(context).visits(), imported)
            put(context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE),
                mapOf("firstVisits" to encodeVisits(merged).toString()))
        }
        if (value.has("vehicleSearchHistory")) {
            val rows = value.getJSONArray("vehicleSearchHistory")
            val history = List(rows.length()) { VehicleSearch.parse(rows.getString(it))!!.keyword }
                .distinct()
            put(context.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE),
                mapOf("history" to JSONArray(history).toString()))
        }
    }

    private fun encodeVisits(visits: List<StationVisit>): JSONArray = JSONArray().apply {
        visits.forEach { visit ->
            put(JSONObject().put("station", visit.station).put("time", visit.firstArrival)
                .put("historical", visit.historicalReminder))
        }
    }
}
