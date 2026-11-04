package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

internal fun quizHistoryDates(date: LocalDate): List<LocalDate> =
    (1L..365L).map(date::minusDays)

internal fun quizRefreshSession(
    original: QuizSession?,
    date: LocalDate,
    version: String,
    questions: List<QuizQuestion>,
): QuizSession {
    val oldIndices = original?.questions.orEmpty().withIndex().associate { it.value to it.index }
    val answers = questions.map { question ->
        oldIndices[question]?.let { original!!.answers[it] } ?: -1
    }
    val times = questions.map { question ->
        oldIndices[question]?.let { original!!.answerTimes[it] }
    }
    val firstUnanswered = answers.indexOfFirst { it < 0 }
    val completed = original?.completed == true && firstUnanswered == -1
    return QuizSession(
        date, version, questions, answers,
        current = if (completed) questions.size else firstUnanswered.coerceAtLeast(0),
        completed = completed,
        answerTimes = times,
        seenKeys = original?.let { it.seenKeys + quizSelectionKeys(it.questions) }.orEmpty(),
    )
}
