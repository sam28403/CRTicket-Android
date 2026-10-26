package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs

/** Count distinct services present on the station's local calendar day, including overnight runs. */
internal fun quizStationCounts(trains: List<QuizTrain>, date: LocalDate): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    for (train in trains) {
        val running = mutableMapOf<Int, Boolean>()
        val seen = mutableSetOf<Pair<String, Int>>()
        for (stop in train.stops) {
            val days = listOfNotNull(stop.arrival, stop.departure).map { it / 1440 }.distinct()
            for (offset in days) {
                if (running.getOrPut(offset) { train.schedule.runs(date.minusDays(offset.toLong())) }
                    && seen.add(stop.station to offset)) {
                    counts[stop.station] = (counts[stop.station] ?: 0) + 1
                }
            }
        }
    }
    return counts
}

internal fun quizStationCountQuestions(
    counts: Map<String, Int>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val stations = counts.keys.sorted()
    val output = mutableListOf<QuizQuestion>()
    for (maximum in listOf(true, false)) {
        var added = 0
        for (station in stations) {
            val seed = "station-count:$maximum:$station"
            val count = counts.getValue(station)
            val tolerance = maxOf(1, count / 5).coerceAtMost(20)
            val comparable = stations.filter {
                it != station && abs(counts.getValue(it) - count) <= tolerance
            }.sortedWith(compareBy<String> { abs(counts.getValue(it) - count) }.thenBy { it }).take(24)
            val group = quizPicks(comparable, seed, 3) + station
            if (group.size != 4) continue
            if (!quizComparableCounts(group.map { counts.getValue(it) })) continue
            val id = "station-count:$maximum:${group.sorted().joinToString(",")}"
            if (!quizEligible(id, date)) continue
            val sorted = group.sortedBy { (counts[it] ?: 0) * if (maximum) -1 else 1 }
            if (counts[sorted[0]] == counts[sorted[1]]) continue
            quizQuestion(
                id, 3,
                QuizText(if (maximum) R.string.quiz_q_station_most else R.string.quiz_q_station_least),
                QuizChoice(sorted[0], QuizOptionKind.STATION),
                sorted.drop(1).map { QuizChoice(it, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_station_count, listOf(sorted[0], counts[sorted[0]].toString())),
            )?.takeIf(keep)?.let { output.add(it); added++ }
            if (added >= 12) break
        }
    }
    return output
}
