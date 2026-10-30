package cc.samlab.ticket.tracking

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Merge independent predictions without treating a missing response as punctual. */
internal object TrainTrackingDelayUpdate {
    fun merge(previous: String, response: JSONObject?, today: LocalDate): JSONObject {
        val old = JSONObject(previous)
        val next = JSONObject((response ?: old).toString())
        val scheduled = old.optJSONArray("scheduledStops") ?: old.getJSONArray("stops")
        val oldRows = old.getJSONArray("stops")
        val newRows = response?.getJSONArray("stops")
        val rows = org.json.JSONArray(scheduled.toString())
        val date = LocalDate.parse(old.getString("date"))
        val keepCache = old.optString("delayCacheDay", today.toString()) == today.toString()
        var available = false
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val offsets = listOf("arrival", "departure").map { key ->
                val field = "${key}Offset"
                val fresh = newRows?.optJSONObject(index)
                val cached = oldRows.optJSONObject(index)
                val offset = when {
                    fresh != null && !fresh.isNull(field) -> fresh.getInt(field)
                    keepCache && cached != null && !cached.isNull(field) -> cached.getInt(field)
                    else -> null
                }
                row.remove(field)
                if (offset != null && !row.isNull(key)) {
                    val day = row.optInt("${key}Day", row.optInt("arrivalDay"))
                    val corrected = TrainDelayTimes.corrected(date.toString(), day, row.getString(key), offset)
                    row.put(field, offset)
                    row.put(key, corrected.toLocalTime().toString().take(5))
                    row.put("${key}Day", ChronoUnit.DAYS.between(date, corrected.toLocalDate()))
                    available = true
                }
                offset
            }
            val stay = if (row.isNull("stay")) null else row.optInt("stay")
            row.put("stay", TrainDelayTimes.stay(stay, offsets[0], offsets[1]) ?: JSONObject.NULL)
        }
        return next.put("scheduledStops", scheduled).put("stops", rows)
            .put("delayEnabled", old.optBoolean("delayEnabled"))
            .put("delayAvailable", available).put("delayCacheDay", today.toString())
    }
}
