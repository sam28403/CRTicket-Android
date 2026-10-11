package cc.samlab.ticket.tracking

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TrainNotificationContent(
    val title: String,
    val text: String,
    val chip: String,
    val route: String,
)

object TrainTrackingText {
    private val railwayZone = ZoneId.of("Asia/Shanghai")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val datedClockFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private fun minutes(milliseconds: Long): Long =
        (milliseconds.coerceAtLeast(0) + 59_999) / 60_000

    fun duration(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        return "${remaining / 60}小时${remaining % 60}分钟"
    }

    fun compactDuration(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        val hours = remaining / 60
        val minutes = remaining % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h${minutes}m"
        }
    }

    fun departureCountdown(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        return when {
            milliseconds > 86_400_000L -> "${remaining / (24 * 60)}d${remaining / 60 % 24}h"
            milliseconds >= 3_600_000L -> "${remaining / 60}h${remaining % 60}m"
            else -> "${remaining}m"
        }
    }

    private fun stationDepartureCountdown(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        return "${remaining / 60}h${remaining % 60}m"
    }

    private fun arrivalTime(timestamp: Long, now: Long): String {
        val arrival = Instant.ofEpochMilli(timestamp).atZone(railwayZone)
        val today = Instant.ofEpochMilli(now).atZone(railwayZone).toLocalDate()
        val format = if (arrival.toLocalDate() == today) clockFormat else datedClockFormat
        return arrival.format(format)
    }

    fun content(timeline: TrainTimeline, now: Long): TrainNotificationContent {
        val status = timeline.status(now)
        val next = timeline.stops.firstOrNull { it.arrival != null && it.arrival > now }
        val route = "${timeline.stops.first().station}→${timeline.stops.last().station}"
        val title = "${timeline.currentTrain(now)} · $route"
        return when (status.phase) {
            TrainPhase.WAITING -> TrainNotificationContent(
                title,
                "等待发车 · ${departureCountdown(timeline.start - now)}后发车",
                departureCountdown(timeline.start - now),
                route,
            )
            TrainPhase.RUNNING -> TrainNotificationContent(
                title,
                "前方到站：${status.station}\n剩余${duration(next!!.arrival!! - now)}",
                compactDuration(next.arrival - now),
                route,
            )
            TrainPhase.AT_STATION -> {
                val current = timeline.stops.first { it.departure == status.nextEvent }
                val departure = stationDepartureCountdown(status.nextEvent!! - now)
                TrainNotificationContent(
                    title,
                    "当前到站：${status.station}，${departure}后出发\n" +
                        "前方到站：${next!!.station}，预计到达：${arrivalTime(next.arrival!!, now)}",
                    TrainStationChip.text(status.station, current.arrival!!, now),
                    route,
                )
            }
            TrainPhase.ARRIVED -> TrainNotificationContent(
                title,
                "已达终点：${status.station}",
                "已达终点",
                route,
            )
        }
    }
}
