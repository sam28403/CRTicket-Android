package cc.samlab.ticket.ui.trainlist

import android.content.Context
import cc.samlab.ticket.R
import org.json.JSONArray
import org.json.JSONObject

internal fun parseTrainListRows(context: Context, data: JSONArray): List<TrainListRow> {
    return List(data.length()) { index -> parseTrainListRow(context, data.getJSONObject(index), index) }
}

internal fun parseTrainListRow(context: Context, row: JSONObject, sourceIndex: Int = 0): TrainListRow {
    val unavailable = context.getString(R.string.display_unavailable)
    val duration = row.optString("duration")
    val minutes = parseTrainListDuration(duration)
    val km = row.optDouble("mileage", Double.NaN)
    val distance = km.takeIf { it.isFinite() && it >= 0 }
    val speed = if (minutes != null && minutes > 0) {
        distance?.let { it * 60 / minutes }
    } else {
        null
    }
    return TrainListRow(
        row.optString("train"),
        row.optString("from").ifBlank { unavailable },
        row.optString("to").ifBlank { unavailable },
        duration.takeIf { it.isNotBlank() && it != "null" } ?: unavailable,
        if (row.isNull("stationCount")) unavailable else row.optString("stationCount"),
        if (distance == null) unavailable else row.optString("mileage"),
        if (speed == null) unavailable
        else context.getString(R.string.display_decimal_one, speed),
        sourceIndex = sourceIndex,
        notRunning = row.optBoolean("notRunning"),
        dataset = row.optString("dataset"),
        distanceKm = distance,
        speedKmh = speed,
        stationCount = row.optDouble("stationCount", Double.NaN)
            .takeIf { it.isFinite() && it >= 0 },
    )
}
