package cc.samlab.ticket.backup

import org.json.JSONArray
import org.json.JSONObject

internal fun validateSpeedometerBackup(rows: JSONArray) {
    require(rows.length() <= 10_000) { "Too many speed recordings" }
    val ids = mutableSetOf<String>()
    var count = 0
    for (index in 0 until rows.length()) {
        val row = rows.getJSONObject(index)
        for (key in listOf("id", "train", "from", "to")) {
            val text = row.get(key)
            require(text is String && text.isNotBlank() && text.length <= 100 &&
                text.none { it.isISOControl() }) { "Invalid recording $key" }
        }
        require(ids.add(row.getString("id"))) { "Duplicate recording ID" }
        if (row.has("vehicle")) {
            val vehicle = row.get("vehicle")
            require(vehicle is String && vehicle.length <= 100 &&
                vehicle.none { it.isISOControl() }) { "Invalid recording vehicle" }
        }
        requireBackupInteger(row.get("started"), 0.0)
        requireBackupInteger(row.get("elapsed"), 60_000.0)
        val meters = row.get("meters")
        require(meters is Number && meters.toDouble().isFinite() && meters.toDouble() >= 0) {
            "Invalid recording distance"
        }
        val points = row.getJSONArray("points")
        count += points.length()
        require(count <= 1_000_000) { "Too many track points" }
        var previousTime = -1.0
        var previousSegment = -1.0
        for (pointIndex in 0 until points.length()) {
            val point = points.getJSONArray(pointIndex)
            require(point.length() == 4) { "Invalid track point" }
            for ((axis, range) in listOf(0 to -90.0..90.0, 1 to -180.0..180.0)) {
                val coordinate = point.get(axis)
                require(coordinate is Number && coordinate.toDouble() in range) { "Invalid track coordinate" }
            }
            val time = requireBackupInteger(point.get(2), 0.0)
            val segment = requireBackupInteger(point.get(3), 0.0)
            require(time > previousTime && segment >= previousSegment && segment <= Int.MAX_VALUE) {
                "Invalid track sequence"
            }
            previousTime = time
            previousSegment = segment
        }
    }
}

private fun requireBackupInteger(value: Any, minimum: Double): Double {
    require(value is Number && value.toDouble().isFinite() &&
        value.toDouble() in minimum..9_007_199_254_740_991.0 && value.toDouble() % 1.0 == 0.0) {
        "Invalid recording timestamp"
    }
    return value.toDouble()
}
