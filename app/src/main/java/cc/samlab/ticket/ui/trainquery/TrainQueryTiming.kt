package cc.samlab.ticket.ui.trainquery

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cc.samlab.ticket.tracking.TimedTrainStop
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import kotlinx.coroutines.delay
import org.json.JSONObject

internal enum class QueryStopHighlight { NONE, UPCOMING, IN_STATION }

internal data class QueryStopTiming(val upcoming: Int = -1, val inStation: Int = -1) {
    fun highlight(index: Int) = when (index) {
        inStation -> QueryStopHighlight.IN_STATION
        upcoming -> QueryStopHighlight.UPCOMING
        else -> QueryStopHighlight.NONE
    }
}

internal fun trainQueryTiming(stops: List<TimedTrainStop>, now: Long): QueryStopTiming {
    val start = stops.firstOrNull()?.departure ?: return QueryStopTiming()
    val end = stops.lastOrNull()?.arrival ?: return QueryStopTiming()
    if (now < start || now >= end) return QueryStopTiming()
    return QueryStopTiming(
        upcoming = stops.indexOfFirst { it.arrival?.let { time -> time > now } == true },
        inStation = stops.indexOfFirst {
            it.arrival != null && it.departure != null && now >= it.arrival && now < it.departure
        },
    )
}

internal fun queryTimeline(result: JSONObject): TrainTimeline? = runCatching {
    if (result.optBoolean("delayAvailable")) {
        return@runCatching cc.samlab.ticket.tracking.TrainTrackingStore.parse(result.toString())
    }
    val stops = result.getJSONArray("stops")
    TrainTimeline(result.getString("train"), result.getString("date"), List(stops.length()) { index ->
        val stop = stops.getJSONObject(index)
        TrainStopSchedule(
            station = stop.getString("station"),
            arrival = stop.optionalText("arrival"),
            departure = stop.optionalText("departure"),
            arrivalDay = if (stop.isNull("arrivalDay")) null else stop.getInt("arrivalDay"),
            departureDay = if (stop.isNull("departureDay")) null else stop.getInt("departureDay"),
            train = stop.optionalText("train"),
        )
    })
}.getOrNull()

@Composable
internal fun rememberQueryTiming(result: JSONObject?, active: Boolean): State<QueryStopTiming> {
    val timeline = remember(result) { result?.let(::queryTimeline) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(QueryStopTiming(), timeline, lifecycle, active) {
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = trainQueryTiming(timeline?.stops.orEmpty(), System.currentTimeMillis())
                delay(1000)
            }
        }
    }
}

private fun JSONObject.optionalText(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
