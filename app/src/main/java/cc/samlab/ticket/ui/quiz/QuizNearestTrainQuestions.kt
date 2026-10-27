package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs

internal fun quizNearestTrainQuestions(
    trains: List<QuizTrain>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> = buildList {
    val targets = listOf(
        QuizMetric.DISTANCE to (100..5000 step 100),
        QuizMetric.DURATION to (60..3420 step 60),
    )
    for ((metric, values) in targets) for (target in values) {
        val window = if (metric == QuizMetric.DISTANCE) target / 5 else quizDurationWindow(target)
        val candidates = trains.filter { train ->
            metric.value(train)?.let { value -> abs(value - target) <= window } == true
        }
        if (candidates.size < 4) continue
        var added = 0
        for (train in candidates) {
            val seed = "train-nearest:${metric.name}:$target:${train.code}"
            val group = quizPicks(candidates.filter { it.code != train.code }, seed, 3) + train
            if (group.size != 4) continue
            val id = "train-nearest:${metric.name}:$target:${group.map { it.code }.sorted().joinToString(",")}"
            if (!quizEligible(id, date)) continue
            val sorted = group.sortedBy { abs(metric.value(it)!! - target) }
            val margin = abs(metric.value(sorted[1])!! - target) - abs(metric.value(sorted[0])!! - target)
            if (margin < 1) continue
            val targetText = if (metric == QuizMetric.DISTANCE) "$target km" else "${target / 60} h"
            quizQuestion(
                id, 4, QuizText(R.string.quiz_q_closest, listOf(metric.token, targetText)),
                sorted[0].choice(), sorted.drop(1).map { it.choice() },
                QuizText(R.string.quiz_f_closest,
                    listOf(sorted[0].code, metric.token, quizMetricValue(sorted[0], metric), targetText)),
            )?.takeIf(keep)?.let { add(it); added++ }
            if (added >= 4) break
        }
    }
}

internal fun quizDurationWindow(targetMinutes: Int): Int = targetMinutes / 10
