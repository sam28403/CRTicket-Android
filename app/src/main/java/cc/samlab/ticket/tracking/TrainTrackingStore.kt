package cc.samlab.ticket.tracking

import android.content.Context
import org.json.JSONObject

object TrainTrackingStore {
    private fun preferences(context: Context) =
        context.getSharedPreferences("train-tracking", Context.MODE_PRIVATE)

    fun read(context: Context): String? = preferences(context).getString("schedule", null)

    fun save(context: Context, payload: String) {
        preferences(context).edit().putString("schedule", payload).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove("schedule").apply()
    }

    fun parse(payload: String): TrainTimeline {
        require(payload.length <= 100_000) { "时刻表数据过大" }
        val data = JSONObject(payload)
        val rows = data.getJSONArray("stops")
        require(rows.length() in 2..200) { "至少需要两个站点才能追踪" }
        val schedules = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            TrainStopSchedule(
                station = row.getString("station"),
                arrival = row.optionalString("arrival"),
                departure = row.optionalString("departure"),
                arrivalDay = row.optionalDay("arrivalDay"),
                departureDay = row.optionalDay("departureDay"),
                train = row.optionalString("train"),
            )
        }
        return TrainTimeline(
            data.getString("train"), data.getString("date"), schedules,
            queryTrain = data.optString("queryTrain", data.getString("train")).take(40),
            querySource = if (data.optString("querySource") == "llt") "llt" else "",
        )
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optionalDay(key: String): Int? {
        if (isNull(key)) return null
        val value = get(key)
        require(value is Number && value.toDouble() == value.toInt().toDouble()) {
            "时刻表跨日数据无效"
        }
        return value.toInt()
    }
}
