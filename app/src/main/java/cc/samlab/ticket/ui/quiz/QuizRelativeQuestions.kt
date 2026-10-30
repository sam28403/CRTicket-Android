package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs

private fun relativeOffset(reference: QuizStation, station: QuizStation, direction: QuizDirection) =
    (direction.axis(station) - direction.axis(reference)) * direction.sign

internal fun quizComparableRelativeChoices(
    reference: QuizStation,
    options: List<QuizStation>,
    direction: QuizDirection,
): Boolean {
    if (options.size != 4 || options.map { it.name }.distinct().size != 4) return false
    val offsets = options.map { relativeOffset(reference, it, direction) }
    if (offsets.count { it >= 0.02 } != 1 || offsets.count { it <= -0.02 } != 3) return false
    val distances = options.map { quizStationDistanceKm(reference, it) }
    val gaps = offsets.map(::abs)
    return distances.all { it in 1.0..60.0 } &&
        distances.max() <= distances.min() * 2 && gaps.max() <= gaps.min() * 2
}

internal fun quizRelativeQuestion(
    reference: QuizStation,
    nearby: List<QuizStation>,
    direction: QuizDirection,
): QuizQuestion? {
    val id = "relative:${direction.name}:${reference.name}"
    val right = nearby.filter { relativeOffset(reference, it, direction) >= 0.02 }
    val wrong = nearby.filter { relativeOffset(reference, it, direction) <= -0.02 }
    for (correct in quizPicks(right, id, right.size)) {
        val distance = quizStationDistanceKm(reference, correct)
        val gap = abs(relativeOffset(reference, correct, direction))
        val comparable = wrong.filter {
            quizStationDistanceKm(reference, it) in (distance / 2)..(distance * 2) &&
                abs(relativeOffset(reference, it, direction)) in (gap / 2)..(gap * 2)
        }
        // Keep all four options in one distance and coordinate-offset band.
        for (anchor in comparable) {
            val lowDistance = minOf(distance, quizStationDistanceKm(reference, anchor))
            val lowGap = minOf(gap, abs(relativeOffset(reference, anchor, direction)))
            val band = comparable.filter {
                quizStationDistanceKm(reference, it) in lowDistance..(lowDistance * 2) &&
                    abs(relativeOffset(reference, it, direction)) in lowGap..(lowGap * 2)
            }
            val others = quizPicks(band, id, 3)
            if (!quizComparableRelativeChoices(reference, others + correct, direction)) continue
            return quizQuestion(
                id, 6, QuizText(R.string.quiz_q_relative, listOf(reference.name, direction.token)),
                QuizChoice(correct.name, QuizOptionKind.STATION),
                others.map { QuizChoice(it.name, QuizOptionKind.STATION) },
                QuizText(R.string.quiz_f_relative, listOf(correct.name, reference.name, direction.token)),
            )
        }
    }
    return null
}

internal fun quizRelativeQuestions(
    stations: List<QuizStation>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean,
): List<QuizQuestion> = buildList {
    val neighborhoods = QuizGeographyIndex.neighbors(stations)
    for (direction in QuizDirection.entries) {
        var added = 0
        for (reference in stations) {
            if (!quizEligible("relative:${direction.name}:${reference.name}", date)) continue
            quizRelativeQuestion(reference, neighborhoods.getValue(reference.name), direction)
                ?.takeIf(keep)?.let { add(it); added++ }
            if (added >= 8) break
        }
    }
}
