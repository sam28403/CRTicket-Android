package cc.samlab.ticket.speedometer

import kotlin.math.*

internal fun speedometerMaximum(train: String): Int {
    val code = train.uppercase()
    val number = code.drop(1).toIntOrNull() ?: 0
    return when (code.firstOrNull()) {
        'G' -> 350
        'D' -> 250
        'C' -> if (number in 1..1000 || number in 4001..4700) 160 else 350
        'S' -> 200
        'Z' -> 160
        'T', 'N' -> 140
        'V' -> 120
        else -> 120
    }
}

internal data class SpeedPoint(
    val latitude: Double,
    val longitude: Double,
    val time: Long,
    val segment: Int,
)

internal data class SpeedRecording(
    val id: String,
    val train: String,
    val from: String,
    val to: String,
    val started: Long,
    val elapsed: Long = 0,
    val meters: Double = 0.0,
    val points: List<SpeedPoint> = emptyList(),
    val vehicle: String = "",
) {
    val canSave: Boolean get() = elapsed >= 60_000
    val average: Double get() = if (elapsed > 0) meters * 3600 / elapsed else 0.0
}

internal fun distanceMeters(a: SpeedPoint, b: SpeedPoint): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Pauses start a new segment; missing GPS fixes deliberately retain the last endpoint. */
internal fun SpeedRecording.append(point: SpeedPoint): SpeedRecording {
    val previous = points.lastOrNull()
    if (previous != null && point.time <= previous.time) return this
    val extra = if (previous != null && previous.segment == point.segment) {
        distanceMeters(previous, point)
    } else 0.0
    return copy(meters = meters + extra, points = points + point)
}

internal fun recordingTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
        seconds / 3600, seconds / 60 % 60, seconds % 60)
}
