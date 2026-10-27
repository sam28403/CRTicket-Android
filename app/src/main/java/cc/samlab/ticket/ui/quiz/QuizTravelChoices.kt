package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

internal fun quizTrainDigits(code: String): Int = code.count(Char::isDigit)

/** Interleave code lengths so a busy, long-numbered service cannot fill the entire pool. */
internal fun quizTravelAnchors(trains: List<QuizTrain>, date: LocalDate): Sequence<QuizTrain> = sequence {
    val groups = trains.groupBy { quizTrainDigits(it.code) }.toSortedMap().values.map { group ->
        group.sortedWith(compareBy<QuizTrain> { quizHash("$date:travel-anchor:${it.code}") }.thenBy { it.code })
    }
    for (index in 0 until (groups.maxOfOrNull { it.size } ?: 0)) {
        for (group in groups) group.getOrNull(index)?.let { yield(it) }
    }
}

internal fun quizDepartureIndex(trains: List<QuizTrain>, date: LocalDate): Map<String, List<QuizTrain>> {
    val result = mutableMapOf<String, MutableList<QuizTrain>>()
    for (train in trains) {
        val stations = train.stops.drop(1).dropLast(1).filter { stop ->
            stop.departure?.let { train.schedule.runs(date.minusDays((it / 1440).toLong())) } == true
        }.map { it.station }.distinct()
        for (station in stations) result.getOrPut(station) { mutableListOf() }.add(train)
    }
    return result
}
