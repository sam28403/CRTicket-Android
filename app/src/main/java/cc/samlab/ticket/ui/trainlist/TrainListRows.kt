package cc.samlab.ticket.ui.trainlist

import android.content.Context
import cc.samlab.ticket.R
import org.json.JSONArray

internal fun parseTrainListRows(context: Context, data: JSONArray): List<TrainListRow> {
    val unavailable = context.getString(R.string.display_unavailable)
    return List(data.length()) { index ->
        val row = data.getJSONObject(index)
        val duration = row.optString("duration")
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        val minutes = if (parts.size == 2) parts[0] * 60 + parts[1] else 0
        val km = row.optDouble("mileage", Double.NaN)
        val distance = km.takeIf { it.isFinite() && it >= 0 }
        val speed = distance?.takeIf { minutes > 0 }?.let { it * 60 / minutes }
        TrainListRow(
            row.optString("train"), row.optString("from"), row.optString("to"),
            duration.takeIf { it.isNotBlank() && it != "null" } ?: unavailable,
            if (row.isNull("stationCount")) unavailable else row.optString("stationCount"),
            if (distance == null) unavailable else row.optString("mileage"),
            if (speed == null) unavailable
            else context.getString(R.string.display_decimal_one, speed),
            sourceIndex = index,
            distanceKm = distance,
            speedKmh = speed,
            stationCount = row.optDouble("stationCount", Double.NaN)
                .takeIf { it.isFinite() && it >= 0 },
        )
    }
}
