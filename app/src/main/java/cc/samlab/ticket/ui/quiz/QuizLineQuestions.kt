package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun quizLineQuestions(
    sources: QuizSources,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val output = mutableListOf<QuizQuestion>()
    val stationNames = sources.stations.map { it.name }.toSet()
    for (line in sources.lines) {
        val members = line.stops.map { it.first }.toSet()
        val known = line.stops.filter { it.first in stationNames && it.second != null }
        var distances = 0
        for (i in known.indices) for (j in i + 1 until known.size) {
            val from = known[i]
            val to = known[j]
            val id = "line-distance:${line.name}:${listOf(from.first, to.first).sorted().joinToString(",") }"
            if (!quizEligible(id, date) || distances >= 4) continue
            val distance = abs(to.second!! - from.second!!)
            if (distance < 30) continue
            // The nearest rounded estimate is uniquely correct and separated from all distractors.
            val step = if (distance < 200) 10 else 50
            val estimate = (distance / step).roundToInt() * step
            val offsets = quizDistanceOffsets(id, estimate, step)
            val correct = QuizChoice("$estimate km", QuizOptionKind.TEXT)
            quizQuestion(
                id, 5, QuizText(R.string.quiz_q_line_distance, listOf(line.name, from.first, to.first)),
                correct, offsets.map {
                    QuizChoice("${estimate + it * step} km", QuizOptionKind.TEXT)
                },
                QuizText(R.string.quiz_f_line_distance,
                    listOf(line.name, from.first, to.first, quizNumber(distance))),
            )?.takeIf(keep)?.let { question ->
                val errors = question.options.map { abs(it.label.substringBefore(' ').toDouble() - distance) }
                if (errors.count { it <= errors[question.correctIndex] } == 1) {
                    output.add(question)
                    distances++
                }
            }
        }
        val nonmembers = sources.stations.filter { it.name !in members }
        var memberships = 0
        for ((name, _) in known) {
            val id = "line-member:${line.name}:$name"
            if (!quizEligible(id, date)) continue
            quizQuestion(
                id, 7, QuizText(R.string.quiz_q_line_member, listOf(line.name)),
                QuizChoice(name, QuizOptionKind.STATION),
                quizPicks(nonmembers, id, 3).map { QuizChoice(it.name, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_line_member, listOf(name, line.name)),
            )?.takeIf(keep)?.let { output.add(it); memberships++ }
            if (memberships >= 4) break
        }
    }
    return output
}

internal fun quizDistanceOffsets(id: String, estimate: Int, step: Int): List<Int> {
    val lower = (-6..-1).filter { estimate + it * step > 0 }
    val rank = (quizHash("$id:distance-rank") % 4).toInt().coerceAtMost(lower.size)
    return quizPicks(lower, "$id:lower", rank) + quizPicks((1..6).toList(), "$id:upper", 3 - rank)
}
