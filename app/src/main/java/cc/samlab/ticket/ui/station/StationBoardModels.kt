package cc.samlab.ticket.ui.station

import org.json.JSONObject

internal enum class StationTab(val key: String, val title: String) {
    ALL("all", "全部信息"),
    DEPARTURE("departure", "出发大屏"),
    ARRIVAL("arrival", "到达大屏"),
    MAP("map", "地图"),
}

internal data class StationBoardRow(
    val id: String,
    val train: String,
    val from: String,
    val to: String,
    val arrival: String,
    val departure: String,
    val stay: String,
    val route: String,
    val time: Double,
    val arrivalSeconds: Double,
    val departureSeconds: Double,
    val highlightStart: Double,
    val highlightEnd: Double,
    val highlightLabel: String,
) {
    fun inStation(seconds: Int) = arrivalSeconds.isFinite() && departureSeconds.isFinite() &&
        seconds >= arrivalSeconds && seconds < departureSeconds

    fun upcoming(seconds: Int) = !inStation(seconds) &&
        seconds >= highlightStart && seconds < highlightEnd
}

internal fun parseStationRow(row: JSONObject) = StationBoardRow(
    id = row.getString("id"),
    train = row.getString("displayTrain"),
    from = row.optString("from"),
    to = row.optString("to"),
    arrival = row.optString("arrivalLabel", "—"),
    departure = row.optString("departureLabel", "—"),
    stay = row.optString("stayLabel", "—"),
    route = row.getString("route"),
    time = row.optDouble("timeSeconds", Double.POSITIVE_INFINITY),
    arrivalSeconds = row.optDouble("arrivalSeconds", Double.POSITIVE_INFINITY),
    departureSeconds = row.optDouble("departureSeconds", Double.POSITIVE_INFINITY),
    highlightStart = row.optDouble("highlightStartSeconds", Double.POSITIVE_INFINITY),
    highlightEnd = row.optDouble("highlightEndSeconds", Double.POSITIVE_INFINITY),
    highlightLabel = row.optString("highlightLabel"),
)

internal fun stationCurrentIndex(rows: List<StationBoardRow>, seconds: Int): Int {
    val highlighted = rows.indexOfFirst { it.inStation(seconds) || it.upcoming(seconds) }
    if (highlighted >= 0) return highlighted
    val next = rows.indexOfFirst { it.time.isFinite() && it.time > seconds }
    return if (next >= 0) next else rows.lastIndex.coerceAtLeast(0)
}
