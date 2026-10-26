package cc.samlab.ticket.ui.quiz

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.ui.report.ISSUES_URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class QuizIssueReport(
    val question: String,
    val reference: String,
    val answer: String,
    val reason: String,
    val answeredAt: String,
) {
    val ready: Boolean get() = reason.isNotBlank()
    val title: String get() = appString(R.string.quiz_report_issue_title, question.lineSequence().first().take(80))

    fun body(): String = appString(
        R.string.quiz_report_template, question, reference, answer,
        reason.trim().ifBlank { appString(R.string.quiz_report_reason_hint) }, answeredAt,
    )

    fun url(includeBody: Boolean = true): String {
        val body = if (includeBody) "&body=${encode(body())}" else ""
        return "$ISSUES_URL/new?title=${encode(title)}$body"
    }

    val needsClipboard: Boolean get() = url().length > 7_000

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

internal fun quizReportTime(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC+08:00'", Locale.ROOT)
    .withZone(ZoneId.of("Asia/Shanghai"))
    .format(Instant.ofEpochMilli(epochMillis))

internal fun quizCanReport(session: QuizSession, index: Int): Boolean =
    session.completed && index in session.questions.indices && session.answers[index] >= 0 &&
        session.answers[index] != session.questions[index].correctIndex
