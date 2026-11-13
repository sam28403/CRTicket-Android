package cc.samlab.ticket.ui.railfan.historical

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

internal data class HistoricalTimetable(
    val train: String,
    val stops: List<JSONObject>,
)

private val versionFormat = Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}")
private val clockFormat = Regex("[0-9]{2}:[0-9]{2}")

internal fun parseHistoricalVersions(rows: JSONArray): List<String> =
    (0 until rows.length()).mapNotNull { rows.opt(it) as? String }
        .filter { versionFormat.matches(it) && isDate(it) }
        .distinct()
        .sortedDescending()

private fun isDate(version: String): Boolean = try {
    LocalDate.parse(version.replace('.', '-'))
    true
} catch (_: DateTimeParseException) {
    false
}

internal fun parseHistoricalTimetable(
    response: JSONObject,
    requestedTrain: String,
): HistoricalTimetable {
    val rows = response.optJSONArray("stops") ?: throw JSONException("Missing timetable stops")
    val train = response.scalar("train_codes") ?: response.scalar("trainNumber") ?: requestedTrain
    val stops = (0 until rows.length()).mapNotNull(rows::optJSONObject).mapIndexed { index, source ->
        val arrival = source.scalar("arrive_time")
        val departure = source.scalar("start_time")
        JSONObject().apply {
            put("no", (index + 1).toString().padStart(2, '0'))
            put("station", source.scalar("station_name") ?: "—")
            put("train", train)
            put("arrival", arrival)
            put("departure", departure)
            put("arrivalDay", source.scalar("arrive_day_diff")?.toIntOrNull())
            put("mileage", source.scalar("mileage"))
            put("stay", stayMinutes(arrival, departure))
        }
    }
    if (rows.length() > 0 && stops.isEmpty()) throw JSONException("Invalid timetable stops")
    return HistoricalTimetable(train, stops)
}

private fun JSONObject.scalar(key: String): String? = when (val value = opt(key)) {
    is String -> value.takeIf { it.isNotBlank() }
    is Number -> value.toString()
    else -> null
}

private fun stayMinutes(arrival: String?, departure: String?): String? {
    if (arrival == null || departure == null ||
        !clockFormat.matches(arrival) || !clockFormat.matches(departure)
    ) return null
    return try {
        ChronoUnit.MINUTES.between(LocalTime.parse(arrival), LocalTime.parse(departure))
            .takeIf { it >= 0 }?.toString()
    } catch (_: DateTimeParseException) {
        null
    }
}
