package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate

internal fun quizCanTravel(train: QuizTrain, from: String, to: String, date: LocalDate): Boolean {
    val fromIndex = train.stops.indexOfFirst { it.station == from }
    val toIndex = train.stops.indexOfLast { it.station == to }
    if (fromIndex < 0 || toIndex <= fromIndex) return false
    val departure = train.stops[fromIndex].departure ?: return false
    val arrival = train.stops[toIndex].arrival ?: return false
    return arrival > departure &&
        train.schedule.runs(date.minusDays((departure / 1440).toLong()))
}

internal fun quizTravelQuestions(
    all: List<QuizTrain>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val output = mutableListOf<QuizQuestion>()
    val seen = mutableSetOf<String>()
    val departures = quizDepartureIndex(all, date)
    val stationSets = all.associate { it.code to it.stops.map { stop -> stop.station }.toSet() }
    for (train in quizTravelAnchors(all, date)) {
        if (train.stops.size < 4) continue
        var added = 0
        pairs@ for (index in 1 until train.stops.lastIndex - 1) {
            for (destination in index + 1 until train.stops.lastIndex) {
                val from = train.stops[index]
                val departure = from.departure ?: continue
                val to = train.stops[destination]
                val id = "travel:${from.station}:${to.station}"
                if (id in seen || !quizEligible(id, date) ||
                    !quizInteriorEndpoints(train, from.station, to.station) ||
                    !quizCanTravel(train, from.station, to.station, date)) continue
                val distractors = departures[from.station].orEmpty().filter { candidate ->
                    candidate.code != train.code && to.station !in stationSets.getValue(candidate.code) &&
                        quizInteriorEndpoints(candidate, from.station, to.station) &&
                        !quizCanTravel(candidate, from.station, to.station, date)
                }
                quizQuestion(
                    id, 10, QuizText(R.string.quiz_q_travel, listOf(from.station, to.station)),
                    train.choice(), quizPicks(distractors, id, 3).map { it.choice() },
                    QuizText(R.string.quiz_f_travel,
                        listOf(train.code, quizClock(departure), from.station, to.station)),
                )?.takeIf(keep)?.let { output.add(it); seen.add(id); added++ }
                if (output.size >= 24) return output
                if (added == 2) break@pairs
            }
        }
    }
    return output
}

