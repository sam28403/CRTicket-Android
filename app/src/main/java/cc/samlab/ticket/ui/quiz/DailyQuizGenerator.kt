package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

internal class DailyQuizGenerator(private val sources: QuizSources) {
    fun generate(
        date: LocalDate,
        vehicleQuestions: List<QuizQuestion> = emptyList(),
        excludedIds: Set<String> = emptySet(),
        seenKeys: Set<String> = emptySet(),
        variationSeed: String = "",
        stationCounts: Map<String, Int> = emptyMap(),
    ): List<QuizQuestion> {
        val varied = seenKeys.isNotEmpty() || variationSeed.isNotEmpty()
        val ordering = if (variationSeed.isEmpty()) date.toString() else "$date:$variationSeed"
        val keep: (QuizQuestion) -> Boolean = { question ->
            question.id !in excludedIds &&
                (seenKeys.isEmpty() || quizSelectionKeys(question).none { it in seenKeys })
        }
        val simple = sources.trains.filter { Regex("^[A-Z]?\\d+$").matches(it.code) && it.stops.size >= 2 }
        val today = simple.filter { it.schedule.runs(date) }
        val pool = buildList {
            addAll(quizGeography(sources.stations, date, keep))
            addAll(quizCityQuestions(sources.stations, date, keep))
            addAll(quizTrainQuestions(today, date, keep))
            addAll(quizStationCountQuestions(stationCounts, date, keep))
            addAll(quizLineQuestions(sources, date, keep))
            addAll(quizRoutingQuestions(sources, date, keep))
            addAll(vehicleQuestions.filter { quizEligible(it.id, date) && keep(it) })
            addAll(quizTravelQuestions(simple, date, keep))
        }.distinctBy { it.id }.filter { it.id !in excludedIds }
        val byCategory = pool.groupBy { it.category }.mapValues { (category, questions) ->
            questions.sortedWith(compareBy<QuizQuestion> { quizHash("$ordering:$category:${it.id}") }.thenBy { it.id })
        }
        val selected = mutableListOf<QuizQuestion>()
        val selectedKeys = mutableSetOf<String>()
        fun available(question: QuizQuestion): Boolean = !varied ||
            quizSelectionKeys(question).none { it in selectedKeys }
        fun select(question: QuizQuestion) {
            selected.add(question)
            if (varied) selectedKeys.addAll(quizSelectionKeys(question))
        }
        val categories = if (byCategory.size > 10) byCategory.keys.sortedBy { quizHash("$date:category:$it") }
            else byCategory.keys.sorted()
        // Cover each supported direction before filling the remaining daily slots.
        for (round in 0..1) for (category in categories) {
            if (selected.size == 20) break
            val questions = byCategory[category].orEmpty()
            val existingDigits = selected.filter { it.category == 10 }
                .map { quizTrainDigits(it.options[it.correctIndex].label) }.toSet()
            val question = if (category == 10) {
                val candidates = questions.filter { candidate ->
                    selected.none { it.id == candidate.id } && available(candidate)
                }
                candidates.firstOrNull { quizTrainDigits(it.options[it.correctIndex].label) !in existingDigits }
                    ?: candidates.firstOrNull()
            } else if (varied) questions.firstOrNull(::available) else questions.getOrNull(round)
            question?.let(::select)
        }
        if (selected.size < 20) {
            val ids = selected.map { it.id }.toSet()
            for (question in pool.filter { it.id !in ids }
                .sortedWith(compareBy<QuizQuestion> { quizHash("$ordering:extra:${it.id}") }.thenBy { it.id })) {
                if (available(question)) select(question)
                if (selected.size == 20) break
            }
        }
        require(selected.size == 20) { "Insufficient verified daily questions" }
        return selected
    }
}
