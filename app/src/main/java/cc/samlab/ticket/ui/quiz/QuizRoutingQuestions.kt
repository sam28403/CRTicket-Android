package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate

internal fun quizRoutingQuestions(
    sources: QuizSources,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val simple = sources.trains.filter { Regex("^[A-Z]?\\d+$").matches(it.code) }
    val indexed = simple.associateBy { it.code }
    val links = mutableMapOf<String, MutableSet<Pair<String, Int>>>()
    for (route in sources.routes) {
        if (date < route.start || date > route.end || route.trains.size < 2) continue
        for ((index, code) in route.trains.withIndex()) {
            val current = indexed[code] ?: continue
            val next = indexed[route.trains[(index + 1) % route.trains.size]] ?: continue
            if (!current.schedule.runs(date) || current.stops.last().station != next.stops.first().station) continue
            val arrival = current.stops.last().arrival ?: continue
            val departure = next.stops.first().departure ?: continue
            val offset = maxOf(0, (arrival - departure + 1439) / 1440)
            val nextDate = date.plusDays(offset.toLong())
            if (nextDate > route.end || !next.schedule.runs(nextDate)) continue
            links.getOrPut(code) { mutableSetOf() }.add(next.code to offset)
        }
    }
    val output = mutableListOf<QuizQuestion>()
    for ((code, choices) in links.toSortedMap()) {
        if (choices.size != 1) continue
        val id = "routing-next:$code"
        if (!quizEligible(id, date)) continue
        val (next, offset) = choices.single()
        val type = quizTrainType(next)
        val distractors = simple.filter {
            it.code != next && it.code != code && quizTrainType(it.code) == type &&
                it.schedule.runs(date.plusDays(offset.toLong()))
        }
        quizQuestion(
            id, 8, QuizText(R.string.quiz_q_next_train, listOf(code)),
            indexed.getValue(next).choice(showRoute = false),
            quizPicks(distractors, id, 3).map { it.choice(showRoute = false) },
            QuizText(R.string.quiz_f_next_train,
                listOf(code, next, date.plusDays(offset.toLong()).toString())),
        )?.takeIf(keep)?.let(output::add)
        if (output.size >= 24) break
    }
    return output
}
