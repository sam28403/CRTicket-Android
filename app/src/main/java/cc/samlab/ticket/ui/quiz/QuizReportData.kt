package cc.samlab.ticket.ui.quiz

import android.content.Context
import cc.samlab.ticket.R

internal fun quizIssueReport(context: Context, session: QuizSession, index: Int, reason: String): QuizIssueReport {
    require(quizCanReport(session, index))
    val question = session.questions[index]
    fun option(position: Int): String = "${'A' + position}. ${question.options[position].label}"
    val text = buildString {
        appendLine(question.title.resolve(context))
        question.options.indices.forEach { appendLine(option(it)) }
    }.trimEnd()
    val time = session.answerTimes.getOrNull(index)?.let(::quizReportTime)
        ?: context.getString(R.string.quiz_report_time_unknown, session.date.toString())
    return QuizIssueReport(
        text,
        "${option(question.correctIndex)}\n${question.statement.resolve(context)}",
        option(session.answers[index]), reason, time,
    )
}
