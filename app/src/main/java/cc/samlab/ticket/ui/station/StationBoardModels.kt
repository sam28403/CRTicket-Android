package cc.samlab.ticket.ui.station

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import org.json.JSONObject

internal enum class StationTab(val key: String, private val textId: Int) {
    ALL("all", R.string.station_tab_all),
    DEPARTURE("departure", R.string.station_tab_departure),
    ARRIVAL("arrival", R.string.station_tab_arrival),
    MAP("map", R.string.station_tab_map);

    val title: String get() = appString(textId)
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
    val arrivalOffset: Int = 0,
    val departureOffset: Int = 0,
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
    arrivalOffset = row.optInt("arrivalOffset"),
    departureOffset = row.optInt("departureOffset"),
)

internal fun stationCurrentIndex(rows: List<StationBoardRow>, seconds: Int): Int {
    val highlighted = rows.indexOfFirst { it.inStation(seconds) || it.upcoming(seconds) }
    if (highlighted >= 0) return highlighted
    val next = rows.indexOfFirst { it.time.isFinite() && it.time > seconds }
    return if (next >= 0) next else rows.lastIndex.coerceAtLeast(0)
}
