package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs

internal enum class QuizDirection(val token: String, val axis: (QuizStation) -> Double, val sign: Int) {
    NORTH("@quiz_north", { it.latitude }, 1),
    SOUTH("@quiz_south", { it.latitude }, -1),
    WEST("@quiz_west", { it.longitude }, -1),
    EAST("@quiz_east", { it.longitude }, 1),
}

internal fun quizGeography(stations: List<QuizStation>, date: LocalDate): List<QuizQuestion> {
    val output = mutableListOf<QuizQuestion>()
    val neighborhoods = QuizGeographyIndex.neighbors(stations)
    for (direction in QuizDirection.entries) {
        var added = 0
        for (anchor in stations) {
            val seed = "geography:${direction.name}:${anchor.name}"
            val others = quizPicks(neighborhoods.getValue(anchor.name), seed, 3)
            if (others.size != 3) continue
            val group = others + anchor
            val id = "geography:${direction.name}:${group.map { it.name }.sorted().joinToString(",")}"
            if (!quizEligible(id, date)) continue
            val ordered = group.sortedByDescending { direction.axis(it) * direction.sign }
            if (abs(direction.axis(ordered[0]) - direction.axis(ordered[1])) < 0.005) continue
            quizQuestion(
                id, 1, QuizText(R.string.quiz_q_extreme, listOf(direction.token)),
                QuizChoice(ordered[0].name, QuizOptionKind.STATION),
                ordered.drop(1).map { QuizChoice(it.name, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_extreme, listOf(ordered[0].name, direction.token)),
            )?.let { output.add(it); added++ }
            if (added >= 8) break
        }
    }
    for (direction in QuizDirection.entries) {
        var added = 0
        for (reference in stations) {
            val id = "relative:${direction.name}:${reference.name}"
            if (!quizEligible(id, date)) continue
            val right = stations.filter {
                (direction.axis(it) - direction.axis(reference)) * direction.sign > 0.3
            }
            val wrong = stations.filter {
                (direction.axis(it) - direction.axis(reference)) * direction.sign < -0.3
            }
            val correct = quizPicks(right, id, 1).firstOrNull() ?: continue
            quizQuestion(
                id, 6, QuizText(R.string.quiz_q_relative, listOf(reference.name, direction.token)),
                QuizChoice(correct.name, QuizOptionKind.STATION),
                quizPicks(wrong, id, 3).map { QuizChoice(it.name, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_relative, listOf(correct.name, reference.name, direction.token)),
            )?.let { output.add(it); added++ }
            if (added >= 8) break
        }
    }
    return output
}
