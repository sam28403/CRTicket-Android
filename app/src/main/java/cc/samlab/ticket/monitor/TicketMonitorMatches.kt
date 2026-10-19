package cc.samlab.ticket.monitor

import org.json.JSONArray
import org.json.JSONObject

object TicketMonitorMatches {
    private fun selections(filters: JSONObject, key: String): List<String> {
        val values = filters.optJSONArray(key) ?: return emptyList()
        return (0 until values.length()).map { values.optString(it) }
    }

    fun ordinary(train: String): Boolean {
        val normalized = train.trim().uppercase()
        if (Regex("(?:[ZTKLY]\\d+|\\d+)").matches(normalized)) return true
        if (!Regex("[DCS]\\d+").matches(normalized)) return false
        val number = normalized.drop(1).toIntOrNull() ?: return false
        return when (normalized.firstOrNull()) {
            'D' -> number in 1..300 || number in 701..800
            'C' -> number in 1..999 || number in 4001..4699
            'S' -> number in 101..999 || number in 1001..2999
            else -> false
        }
    }

    fun matches(row: JSONObject, filters: JSONObject): Boolean {
        val train = row.optString("train").trim().uppercase()
        val trains = selections(filters, "trainFilters").map { it.trim().uppercase() }
        if (trains.isNotEmpty() && train !in trains) return false
        val highSpeed = filters.optBoolean("highSpeedOnly")
        val ordinary = filters.optBoolean("ordinaryOnly")
        if ((highSpeed || ordinary) && !(if (ordinary(train)) ordinary else highSpeed)) return false
        for ((key, field) in listOf(
            "selectedDepartureStations" to "from", "selectedArrivalStations" to "to",
        )) {
            val stations = selections(filters, key)
            if (stations.isNotEmpty() && row.optString(field) !in stations) return false
        }
        val time = row.optString("departure").split(':').map { it.toIntOrNull() }
        val range = filters.optJSONArray("timeRange")
        if (time.size == 2 && time[0] in 0..23 && time[1] in 0..59 && range != null) {
            val minute = time[0]!! * 60 + time[1]!!
            if (minute < range.optInt(0, 0) * 60 || minute >= range.optInt(1, 24) * 60) return false
        }
        return true
    }

    fun collect(state: JSONObject, rows: JSONArray, config: TicketMonitorConfig): List<String> {
        val filters = config.filters
        val wanted = selections(filters, "seatFilters")
        val columns = TicketSeats.columns.filterKeys { wanted.isEmpty() || it in wanted }
        val previous = state.optJSONObject("previous") ?: JSONObject()
        val next = JSONObject()
        val notices = mutableListOf<String>()
        val events = mutableListOf<JSONObject>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (!matches(row, filters) || !row.optBoolean("canBuy")) continue
            val seats = row.getJSONObject("seats")
            val available = columns.filterKeys { TicketSeats.available(seats.optString(it)) }
            if (available.isEmpty()) continue
            val id = row.getString("id")
            val signature = JSONArray(columns.keys.map { listOf(it, seats.optString(it)) }).toString()
            next.put(id, signature)
            if (previous.optString(id) == signature) continue
            val detail = available.map { (key, label) -> "$label：${seats.optString(key)}" }.joinToString("，")
            val text = "${row.optString("train")} ${row.optString("from")} → ${row.optString("to")}，$detail"
            notices.add(text)
            events.add(JSONObject().put("at", state.optString("queriedAt")).put("text", text))
        }
        val old = state.optJSONArray("events") ?: JSONArray()
        for (i in 0 until old.length()) events.add(old.getJSONObject(i))
        state.put("previous", next).put("events", JSONArray(events.take(30)))
        return notices
    }
}
