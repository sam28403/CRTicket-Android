package cc.samlab.ticket.ui.station

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

internal enum class StationHighlight { NONE, UPCOMING, IN_STATION }

internal data class StationBoardTiming(
    val currentIndex: Int,
    val inStation: Set<String>,
    val upcoming: Set<String>,
) {
    fun highlight(id: String) = when (id) {
        in inStation -> StationHighlight.IN_STATION
        in upcoming -> StationHighlight.UPCOMING
        else -> StationHighlight.NONE
    }
}

internal fun stationBoardTiming(rows: List<StationBoardRow>, seconds: Int) = StationBoardTiming(
    currentIndex = stationCurrentIndex(rows, seconds),
    inStation = rows.filter { it.inStation(seconds) }.mapTo(mutableSetOf()) { it.id },
    upcoming = rows.filter { it.upcoming(seconds) }.mapTo(mutableSetOf()) { it.id },
)

@Composable
internal fun rememberStationBoardTiming(rows: List<StationBoardRow>, active: Boolean): State<StationBoardTiming> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val seconds = remember {
        mutableIntStateOf(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toLocalTime().toSecondOfDay())
    }
    LaunchedEffect(active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val zone = ZoneId.of("Asia/Shanghai")
            while (true) {
                seconds.intValue = ZonedDateTime.now(zone).toLocalTime().toSecondOfDay()
                delay(1000)
            }
        }
    }
    // Equal highlight sets suppress recomposition between actual train events.
    return remember(rows) { derivedStateOf { stationBoardTiming(rows, seconds.intValue) } }
}
