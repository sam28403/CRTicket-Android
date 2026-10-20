package cc.samlab.ticket.tracking

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
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
        return appString(R.string.duration_hours_minutes, remaining / 60, remaining % 60)
    }

    fun compactDuration(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        val hours = remaining / 60
        val minutes = remaining % 60
        return when {
            hours == 0L -> appString(R.string.duration_compact_minutes, minutes)
            minutes == 0L -> appString(R.string.duration_compact_hours, hours)
            else -> appString(R.string.duration_compact_hours_minutes, hours, minutes)
        }
    }

    fun departureCountdown(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        return when {
            milliseconds > 86_400_000L -> appString(
                R.string.duration_compact_days_hours,
                remaining / (24 * 60),
                remaining / 60 % 24,
            )
            milliseconds >= 3_600_000L -> appString(
                R.string.duration_compact_hours_minutes,
                remaining / 60,
                remaining % 60,
            )
            else -> appString(R.string.duration_compact_minutes, remaining)
        }
    }

    private fun stationDepartureCountdown(milliseconds: Long): String {
        val remaining = minutes(milliseconds)
        return appString(R.string.duration_compact_hours_minutes, remaining / 60, remaining % 60)
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
                appString(R.string.tracking_notification_waiting, departureCountdown(timeline.start - now)),
                departureCountdown(timeline.start - now),
                route,
            )
            TrainPhase.RUNNING -> TrainNotificationContent(
                title,
                appString(R.string.tracking_notification_running, status.station, duration(next!!.arrival!! - now)),
                compactDuration(next.arrival - now),
                route,
            )
            TrainPhase.AT_STATION -> {
                val current = timeline.stops.first { it.departure == status.nextEvent }
                val departure = stationDepartureCountdown(status.nextEvent!! - now)
                TrainNotificationContent(
                    title,
                    appString(
                        R.string.tracking_notification_at_station,
                        status.station,
                        departure,
                        next!!.station,
                        arrivalTime(next.arrival!!, now),
                    ),
                    TrainStationChip.text(status.station, current.arrival!!, now),
                    route,
                )
            }
            TrainPhase.ARRIVED -> TrainNotificationContent(
                title,
                appString(R.string.tracking_notification_arrived, status.station),
                appString(R.string.tracking_chip_arrived),
                route,
            )
        }
    }
}
