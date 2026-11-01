package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

/** Every train competes in the same seeded shuffle, regardless of its code length. */
internal fun quizTravelAnchors(
    trains: List<QuizTrain>,
    seed: String,
): Sequence<QuizTrain> = trains.sortedWith(
    compareBy<QuizTrain> { quizHash("$seed:travel-anchor:${it.code}") }.thenBy { it.code },
).asSequence()

internal fun quizTravelPairs(train: QuizTrain, seed: String): List<Pair<Int, Int>> = buildList {
    for (from in 1 until train.stops.lastIndex - 1) {
        for (to in from + 1 until train.stops.lastIndex) add(from to to)
    }
}.sortedWith(compareBy<Pair<Int, Int>> {
    quizHash("$seed:travel-pair:${train.code}:${it.first}:${it.second}")
}.thenBy { it.first }.thenBy { it.second })

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
