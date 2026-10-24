package cc.samlab.ticket.tracking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

internal object TrainDelayTimes {
    fun fallbackOffset(delayText: String, earlyText: String): Int? {
        val delay = delayText.toIntOrNull()?.takeIf { it >= 0 }
        val early = earlyText.toIntOrNull()
        return when {
            early != null && early != 0 -> -abs(early)
            early == 0 -> 0
            delay != null -> delay
            else -> null
        }
    }

    fun predictionOffset(scheduled: String, message: String): Int? {
        val predicted = Regex("(?:到达|出发)时间为\\s*((?:[01]\\d|2[0-3]):[0-5]\\d)")
            .find(message)?.groupValues?.get(1)
        return when {
            predicted != null -> nearestOffset(scheduled, predicted)
            message.contains("预计正点") -> 0
            else -> null
        }
    }

    fun minutes(clock: String): Int = LocalTime.parse(clock).let { it.hour * 60 + it.minute }

    fun nearestOffset(scheduled: String, predicted: String): Int {
        val difference = minutes(predicted) - minutes(scheduled)
        return listOf(difference, difference - 1440, difference + 1440).minBy { abs(it) }
    }

    fun corrected(date: String, day: Int, clock: String, offset: Int): LocalDateTime =
        LocalDate.parse(date).plusDays(day.toLong()).atTime(LocalTime.parse(clock))
            .plusMinutes(offset.toLong())

    fun stay(original: Int?, arrivalOffset: Int?, departureOffset: Int?): Int? {
        if (original == null) return null
        val actual = original + (departureOffset ?: 0) - (arrivalOffset ?: 0)
        return if (actual > 0) actual else original
    }
}
