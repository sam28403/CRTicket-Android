package cc.samlab.ticket.tracking

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TrainStopSchedule(
    val station: String,
    val arrival: String?,
    val departure: String?,
    val arrivalDay: Int? = null,
    val departureDay: Int? = null,
    val train: String? = null,
)

data class TimedTrainStop(
    val station: String,
    val arrival: Long?,
    val departure: Long?,
    val train: String,
)

enum class TrainPhase { WAITING, RUNNING, AT_STATION, ARRIVED }

data class TrainTrackingStatus(
    val phase: TrainPhase,
    val text: String,
    val station: String,
    val nextEvent: Long?,
    val progress: Int,
)

class TrainTimeline(
    val train: String,
    val date: String,
    schedules: List<TrainStopSchedule>,
    val queryTrain: String = train,
    val querySource: String = "",
    corrections: List<Pair<Int?, Int?>> = emptyList(),
) {
    val stops: List<TimedTrainStop>
    val start: Long
    val end: Long

    init {
        require(train.isNotBlank() && train.length <= 40) { appString(R.string.tracking_error_train) }
        require(schedules.size in 2..200) { appString(R.string.tracking_error_stop_count) }
        val baseDate = LocalDate.parse(date)
        val zone = ZoneId.of("Asia/Shanghai")
        var previous: Long? = null
        var day = 0
        var currentTrain = train

        fun timestamp(clock: String?, explicitDay: Int?): Long {
            require(clock != null && clock.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) {
                appString(R.string.tracking_error_missing_times)
            }
            val time = LocalTime.parse(clock)
            if (explicitDay != null) {
                require(explicitDay in 0..14) { appString(R.string.tracking_error_day) }
                day = explicitDay
            }
            var value = baseDate.plusDays(day.toLong()).atTime(time)
                .atZone(zone).toInstant().toEpochMilli()
            if (explicitDay == null && previous != null && value < previous!!) {
                day++
                value = baseDate.plusDays(day.toLong()).atTime(time)
                    .atZone(zone).toInstant().toEpochMilli()
            }
            require(previous == null || value >= previous!!) { appString(R.string.tracking_error_time_order) }
            previous = value
            return value
        }

        val plannedStops = schedules.mapIndexed { index, stop ->
            require(stop.station.isNotBlank() && stop.station.length <= 80) { appString(R.string.tracking_error_station) }
            val arrival = if (index == 0) null else timestamp(stop.arrival, stop.arrivalDay)
            val departure = if (index == schedules.lastIndex) null
                else timestamp(stop.departure, stop.departureDay)
            stop.train?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                require(code.length <= 40) { appString(R.string.tracking_error_station_train) }
                currentTrain = code
            }
            TimedTrainStop(stop.station, arrival, departure, currentTrain)
        }
        var lastEvent = Long.MIN_VALUE
        stops = plannedStops.mapIndexed { index, stop ->
            val correction = corrections.getOrNull(index)
            val arrival = stop.arrival?.let {
                (it + (correction?.first ?: 0) * 60_000L).coerceAtLeast(lastEvent)
            }
            val departure = stop.departure?.let {
                val predicted = it + (correction?.second ?: 0) * 60_000L
                if (arrival != null && predicted <= arrival) {
                    arrival + (it - stop.arrival!!)
                } else predicted.coerceAtLeast(lastEvent)
            }
            lastEvent = departure ?: arrival ?: lastEvent
            stop.copy(arrival = arrival, departure = departure)
        }
        start = stops.first().departure!!
        end = stops.last().arrival!!
        require(end > start) { appString(R.string.tracking_error_duration) }
    }

    fun currentTrain(now: Long): String = stops.lastOrNull { stop ->
        stop.arrival != null && now >= stop.arrival
    }?.train ?: stops.first().train

    fun status(now: Long): TrainTrackingStatus {
        val progress = (((now - start).coerceIn(0, end - start) * 100) / (end - start)).toInt()
        if (now < start) {
            return TrainTrackingStatus(
                TrainPhase.WAITING, appString(
                    R.string.tracking_departure_countdown,
                    TrainTrackingText.duration(start - now),
                ),
                stops.first().station, start, progress,
            )
        }
        if (now >= end) {
            return TrainTrackingStatus(
                TrainPhase.ARRIVED, appString(R.string.tracking_arrived), stops.last().station, null, 100,
            )
        }
        for (stop in stops.drop(1)) {
            val arrival = stop.arrival!!
            if (now < arrival) {
                return TrainTrackingStatus(
                    TrainPhase.RUNNING,
                    appString(R.string.tracking_next_station, stop.station, TrainTrackingText.duration(arrival - now)),
                    stop.station, arrival, progress,
                )
            }
            if (stop.departure != null && now < stop.departure) {
                return TrainTrackingStatus(
                    TrainPhase.AT_STATION, appString(R.string.tracking_current_station, stop.station),
                    stop.station, stop.departure, progress,
                )
            }
        }
        error(appString(R.string.tracking_error_status))
    }
}
