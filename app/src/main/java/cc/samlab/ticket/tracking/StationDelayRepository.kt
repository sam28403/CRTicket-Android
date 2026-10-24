package cc.samlab.ticket.tracking

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.Executors

internal object StationDelayRepository {
    private val workers = Executors.newFixedThreadPool(4)

    fun query(payload: String): JSONArray {
        require(payload.length <= 200_000)
        val data = JSONObject(payload)
        val station = data.getString("station")
        val date = LocalDate.parse(data.getString("date"))
        require(date == LocalDate.now(ZoneId.of("Asia/Shanghai")))
        val rows = data.getJSONArray("trains")
        require(rows.length() in 1..16 && station.length in 1..30)
        TrainDelayRepository.initSession()
        val tasks = List(rows.length()) { index -> Callable {
            val row = rows.getJSONObject(index)
            val result = JSONObject().put("id", row.getString("id"))
            val code = row.getString("queryTrain")
            if (!code.matches(Regex("[A-Z]?\\d{1,5}"))) return@Callable result
            val offsets = arrayOfNulls<Int>(2)
            listOf("arrival", "departure").forEachIndexed { direction, key ->
                if (!row.isNull(key)) offsets[direction] = runCatching {
                    TrainDelayRepository.prediction(code, station, row.getString(key), direction)
                }.getOrNull()
            }
            if (listOf("arrival", "departure").withIndex().any { (direction, key) ->
                    !row.isNull(key) && offsets[direction] == null
                }) {
                runCatching {
                    val fallback = TrainDelayRepository.fallbackStops(code, row.getString("trainDate"))
                    val matches = if (fallback == null) emptyList() else
                        (0 until fallback.length()).map { fallback.getJSONObject(it) }
                            .filter { it.optString("stationName") == station &&
                                it.optString("trainDate").replace("-", "").take(8) == date.toString().replace("-", "") }
                    if (matches.size == 1) {
                        val item = matches.single()
                        val offset = TrainDelayTimes.fallbackOffset(
                            item.optString("ticketDelay"), item.optString("ticketEarly"),
                        )
                        listOf("arrival", "departure").forEachIndexed { direction, key ->
                            if (!row.isNull(key) && offsets[direction] == null) offsets[direction] = offset
                        }
                    }
                }
            }
            offsets.forEachIndexed { direction, offset ->
                if (offset != null) result.put(if (direction == 0) "arrivalOffset" else "departureOffset", offset)
            }
            result
        } }
        return JSONArray(workers.invokeAll(tasks).mapNotNull { runCatching { it.get() }.getOrNull() })
    }
}
