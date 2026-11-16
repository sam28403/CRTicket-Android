package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import java.time.LocalDate

internal const val QUIZ_STOP_EXTREME = 12
internal const val QUIZ_STOP_EXACT = 13

private enum class StopCountPrompt(val slug: String, val title: Int, val category: Int) {
    MOST("most", R.string.quiz_q_stop_count_most, QUIZ_STOP_EXTREME),
    FEWEST("fewest", R.string.quiz_q_stop_count_fewest, QUIZ_STOP_EXTREME),
    EXACT("exact", R.string.quiz_q_stop_count_exact, QUIZ_STOP_EXACT),
}

internal fun quizStopCountQuestions(
    trains: List<QuizTrain>,
    date: LocalDate,
    seed: String,
    keep: (QuizQuestion) -> Boolean,
): List<QuizQuestion> = buildList {
    val byCount = trains.distinctBy { it.code }.groupBy { it.stops.size }
    for (prompt in StopCountPrompt.entries) {
        val counts = if (prompt == StopCountPrompt.EXACT) (2..49).toList() else byCount.keys
        for (count in counts) {
            val answers = byCount[count].orEmpty()
            if (answers.isEmpty()) continue
            val spread = (count / 5).coerceIn(1, 3)
            val windows = when (prompt) {
                StopCountPrompt.MOST -> count - spread..count - spread
                StopCountPrompt.FEWEST -> count..count
                StopCountPrompt.EXACT -> count - spread..count
            }.map { start ->
                (start..start + spread).filter { it != count }.flatMap { byCount[it].orEmpty() }
            }.filter { it.size >= 3 }
            if (windows.isEmpty()) continue
            val prefix = "train-stop-count:${prompt.slug}:$count"
            val question = answers.firstNotNullOfOrNull { answer ->
                windows.firstNotNullOfOrNull { candidates ->
                    (0..3).firstNotNullOfOrNull { attempt ->
                        val others = quizPicks(candidates, "$seed:$prefix:${answer.code}:$attempt", 3)
                        val codes = (others + answer).map { it.code }.sorted().joinToString(",")
                        val id = "$prefix:$codes"
                        if (!quizEligible(id, date)) null else quizQuestion(
                            id, prompt.category,
                            QuizText(prompt.title, if (prompt == StopCountPrompt.EXACT) listOf("$count") else emptyList()),
                            answer.choice(),
                            others.map { it.choice() },
                            QuizText(R.string.quiz_f_stop_count, listOf(answer.code, "$count")),
                        )?.takeIf(keep)
                    }
                }
            }
            question?.let(::add)
        }
    }
}
