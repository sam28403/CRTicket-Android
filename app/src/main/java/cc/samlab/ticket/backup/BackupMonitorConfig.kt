package cc.samlab.ticket.backup

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Backups may contain an old travel date; restoring settings never starts a monitoring session. */
internal fun validateBackupMonitorConfig(value: JSONObject) {
    for (key in listOf("from", "to")) {
        require(Regex("[A-Z]{3}").matches(value.getString(key))) { "Invalid monitor station" }
    }
    val date = value.getString("date")
    require(LocalDate.parse(date).toString() == date) { "Invalid monitor date" }
    val interval = value.get("interval")
    require(interval is Number && interval.toDouble() in 10.0..3600.0 &&
        interval.toDouble() == interval.toLong().toDouble()) { "Invalid monitor interval" }
    for (key in listOf("fromName", "toName")) {
        require(!value.has(key) || (value.get(key) is String && value.getString(key).length <= 50))
    }
    val filters = if (value.has("filters")) value.getJSONObject("filters") else JSONObject()
    for (key in listOf("trainFilters", "seatFilters", "selectedDepartureStations", "selectedArrivalStations")) {
        if (!filters.has(key)) continue
        val values = filters.getJSONArray(key)
        require(values.length() <= 10000)
        for (index in 0 until values.length()) {
            require(values.get(index) is String && values.getString(index).length <= 100)
        }
    }
    for (key in listOf("highSpeedOnly", "ordinaryOnly")) {
        require(!filters.has(key) || filters.get(key) is Boolean)
    }
    if (filters.has("timeRange")) {
        val range: JSONArray = filters.getJSONArray("timeRange")
        require(range.length() == 2 && range.get(0) is Number && range.get(1) is Number)
        require(range.getDouble(0) in 0.0..24.0 && range.getDouble(1) in range.getDouble(0)..24.0)
    }
}
