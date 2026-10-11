package cc.samlab.ticket.tracking

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
) {
    val stops: List<TimedTrainStop>
    val start: Long
    val end: Long

    init {
        require(train.isNotBlank() && train.length <= 40) { "车次无效" }
        require(schedules.size in 2..200) { "至少需要两个站点才能追踪" }
        val baseDate = LocalDate.parse(date)
        val zone = ZoneId.of("Asia/Shanghai")
        var previous: Long? = null
        var day = 0
        var currentTrain = train

        fun timestamp(clock: String?, explicitDay: Int?): Long {
            require(clock != null && clock.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) {
                "时刻表缺少有效的到发时间，无法追踪"
            }
            val time = LocalTime.parse(clock)
            if (explicitDay != null) {
                require(explicitDay in 0..14) { "时刻表跨日数据无效" }
                day = explicitDay
            }
            var value = baseDate.plusDays(day.toLong()).atTime(time)
                .atZone(zone).toInstant().toEpochMilli()
            if (explicitDay == null && previous != null && value < previous!!) {
                day++
                value = baseDate.plusDays(day.toLong()).atTime(time)
                    .atZone(zone).toInstant().toEpochMilli()
            }
            require(previous == null || value >= previous!!) { "时刻表到发顺序无效" }
            previous = value
            return value
        }

        stops = schedules.mapIndexed { index, stop ->
            require(stop.station.isNotBlank() && stop.station.length <= 80) { "站名无效" }
            val arrival = if (index == 0) null else timestamp(stop.arrival, stop.arrivalDay)
            val departure = if (index == schedules.lastIndex) null
                else timestamp(stop.departure, stop.departureDay)
            stop.train?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                require(code.length <= 40) { "站点车次号无效" }
                currentTrain = code
            }
            TimedTrainStop(stop.station, arrival, departure, currentTrain)
        }
        start = stops.first().departure!!
        end = stops.last().arrival!!
        require(end > start) { "时刻表运行时长无效" }
    }

    fun currentTrain(now: Long): String = stops.lastOrNull { stop ->
        stop.arrival != null && now >= stop.arrival
    }?.train ?: stops.first().train

    fun status(now: Long): TrainTrackingStatus {
        val progress = (((now - start).coerceIn(0, end - start) * 100) / (end - start)).toInt()
        if (now < start) {
            return TrainTrackingStatus(
                TrainPhase.WAITING, "${TrainTrackingText.duration(start - now)}后发车",
                stops.first().station, start, progress,
            )
        }
        if (now >= end) {
            return TrainTrackingStatus(
                TrainPhase.ARRIVED, "列车已经到达目的地", stops.last().station, null, 100,
            )
        }
        for (stop in stops.drop(1)) {
            val arrival = stop.arrival!!
            if (now < arrival) {
                return TrainTrackingStatus(
                    TrainPhase.RUNNING,
                    "前方到站${stop.station}，剩余${TrainTrackingText.duration(arrival - now)}",
                    stop.station, arrival, progress,
                )
            }
            if (stop.departure != null && now < stop.departure) {
                return TrainTrackingStatus(
                    TrainPhase.AT_STATION, "当前到站${stop.station}",
                    stop.station, stop.departure, progress,
                )
            }
        }
        error("时刻表状态无效")
    }
}
