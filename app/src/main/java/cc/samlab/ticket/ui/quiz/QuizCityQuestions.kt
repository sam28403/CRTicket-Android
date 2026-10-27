package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate

internal fun quizCityQuestions(
    stations: List<QuizStation>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val known = stations.filter { it.city.isNotBlank() }
    val output = mutableListOf<QuizQuestion>()
    for ((city, members) in known.groupBy { it.city }.toSortedMap()) {
        val eligible = known.filter { !it.name.contains(city) }
        val wrong = eligible.filter { it.city != city }
        var added = 0
        for (station in members.sortedBy { it.name }) {
            val id = "city-station:$city:${station.name}"
            if (station.name.contains(city) || !quizEligible(id, date)) continue
            quizQuestion(
                id, 11, QuizText(R.string.quiz_q_city, listOf(city)),
                QuizChoice(station.name, QuizOptionKind.STATION),
                quizPicks(wrong, id, 3).map { QuizChoice(it.name, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_city, listOf(station.name, city)),
            )?.takeIf(keep)?.let { output.add(it); added++ }
            if (added == 4) break
        }
    }
    return output
}
