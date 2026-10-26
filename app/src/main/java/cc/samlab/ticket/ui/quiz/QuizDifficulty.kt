package cc.samlab.ticket.ui.quiz

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun quizStationDistanceKm(a: QuizStation, b: QuizStation): Double {
    val latA = Math.toRadians(a.latitude)
    val latB = Math.toRadians(b.latitude)
    val lat = sin((latB - latA) / 2)
    val lon = sin(Math.toRadians(b.longitude - a.longitude) / 2)
    val value = lat * lat + cos(latA) * cos(latB) * lon * lon
    return 6371.0088 * 2 * asin(sqrt(value.coerceIn(0.0, 1.0)))
}

internal fun quizComparableCounts(values: List<Int>): Boolean {
    if (values.size != 4 || values.any { it <= 0 }) return false
    return values.max() <= values.min() * 1.5 && values.max() - values.min() <= 30
}

internal fun quizTrainType(code: String): String =
    code.firstOrNull()?.takeIf { it in 'A'..'Z' }?.toString() ?: "numeric"

internal fun quizInteriorEndpoints(train: QuizTrain, from: String, to: String): Boolean =
    train.stops.first().station !in listOf(from, to) && train.stops.last().station !in listOf(from, to)

internal fun quizCompletionRoute(embedded: Boolean, smallestWidthDp: Int): String? =
    if (embedded || smallestWidthDp >= 600) "/" else null
