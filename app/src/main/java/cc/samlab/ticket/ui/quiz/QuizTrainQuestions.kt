package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate
import kotlin.math.abs

internal enum class QuizMetric(val token: String, val value: (QuizTrain) -> Double?) {
    DISTANCE("@train_list_sort_mileage", { it.distance }),
    DURATION("@train_list_sort_duration", { it.duration?.toDouble() }),
    SPEED("@train_list_sort_speed", { it.speed }),
}

internal fun quizMetricValue(train: QuizTrain, metric: QuizMetric): String = when (metric) {
    QuizMetric.DISTANCE -> "${quizNumber(train.distance!!)} km"
    QuizMetric.DURATION -> {
        val minutes = train.duration!!
        "${minutes / 60} h ${minutes % 60} min"
    }
    QuizMetric.SPEED -> "${quizNumber(train.speed!!)} km/h"
}

internal fun quizTrainQuestions(
    trains: List<QuizTrain>,
    date: LocalDate,
    keep: (QuizQuestion) -> Boolean = { true },
): List<QuizQuestion> {
    val output = mutableListOf<QuizQuestion>()
    for (metric in QuizMetric.entries) {
        val candidates = trains.filter { metric.value(it)?.let { value -> value > 0 } == true }
        var added = 0
        for (train in candidates) {
            val seed = "train-maximum:${metric.name}:${train.code}"
            val group = quizPicks(candidates.filter { it.code != train.code }, seed, 3) + train
            if (group.size != 4) continue
            val id = "train-maximum:${metric.name}:${group.map { it.code }.sorted().joinToString(",")}"
            if (!quizEligible(id, date)) continue
            val sorted = group.sortedByDescending { metric.value(it)!! }
            if (abs(metric.value(sorted[0])!! - metric.value(sorted[1])!!) < 1) continue
            quizQuestion(
                id, 2, QuizText(R.string.quiz_q_train_metric,
                    listOf("@quiz_max_${metric.name.lowercase(java.util.Locale.ROOT)}")),
                sorted[0].choice(),
                sorted.drop(1).map { it.choice() },
                QuizText(R.string.quiz_f_train_metric,
                    listOf(sorted[0].code, metric.token, quizMetricValue(sorted[0], metric))),
            )?.takeIf(keep)?.let { output.add(it); added++ }
            if (added >= 12) break
        }
    }
    output.addAll(quizNearestTrainQuestions(trains, date, keep))
    return output
}
