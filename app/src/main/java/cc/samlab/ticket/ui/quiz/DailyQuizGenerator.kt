package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

internal class DailyQuizGenerator(private val sources: QuizSources) {
    fun generate(
        date: LocalDate,
        vehicleQuestions: List<QuizQuestion> = emptyList(),
        excludedIds: Set<String> = emptySet(),
    ): List<QuizQuestion> {
        val simple = sources.trains.filter { Regex("^[A-Z]?\\d+$").matches(it.code) && it.stops.size >= 2 }
        val today = simple.filter { it.schedule.runs(date) }
        val pool = buildList {
            addAll(quizGeography(sources.stations, date))
            addAll(quizTrainQuestions(today, date))
            addAll(quizStationCountQuestions(quizStationCounts(sources.trains, date), date))
            addAll(quizLineQuestions(sources, date))
            addAll(quizRoutingQuestions(sources, date))
            addAll(vehicleQuestions.filter { quizEligible(it.id, date) })
            addAll(quizTravelQuestions(simple, date))
        }.distinctBy { it.id }.filter { it.id !in excludedIds }
        val byCategory = pool.groupBy { it.category }.mapValues { (category, questions) ->
            questions.sortedWith(compareBy<QuizQuestion> { quizHash("$date:$category:${it.id}") }.thenBy { it.id })
        }
        val selected = mutableListOf<QuizQuestion>()
        // Two per supported direction; missing real-time evidence is filled from supported categories.
        for (round in 0..1) for (category in 1..10) {
            byCategory[category]?.getOrNull(round)?.let(selected::add)
        }
        if (selected.size < 20) {
            val ids = selected.map { it.id }.toSet()
            selected += pool.filter { it.id !in ids }
                .sortedWith(compareBy<QuizQuestion> { quizHash("$date:extra:${it.id}") }.thenBy { it.id })
                .take(20 - selected.size)
        }
        require(selected.size == 20) { "Insufficient verified daily questions" }
        return selected
    }
}
