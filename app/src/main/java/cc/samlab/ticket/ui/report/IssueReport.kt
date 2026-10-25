package cc.samlab.ticket.ui.report

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import java.net.URLEncoder

internal const val ISSUES_URL = "https://github.com/sam28403/CRTicket-Android/issues"

internal enum class ReportKind(private val textId: Int) {
    BUG(R.string.report_bug),
    FEATURE(R.string.report_feature),
    QUESTION(R.string.report_question);

    val title: String get() = appString(textId)
}

internal data class IssueReport(
    val kind: ReportKind,
    val title: String,
    val description: String,
    val steps: String,
    val expected: String,
    val appVersion: String,
    val device: String?,
) {
    val ready: Boolean get() = title.isNotBlank() && description.isNotBlank()

    fun body(): String = buildString {
        appendLine(appString(R.string.report_heading_kind))
        appendLine(kind.title)
        appendLine()
        appendLine(appString(R.string.report_heading_description))
        appendLine(description.trim())
        if (steps.isNotBlank()) {
            appendLine()
            appendLine(appString(R.string.report_heading_steps))
            appendLine(steps.trim())
        }
        if (expected.isNotBlank()) {
            appendLine()
            appendLine(appString(R.string.report_heading_expected))
            appendLine(expected.trim())
        }
        appendLine()
        appendLine(appString(R.string.report_heading_version))
        appendLine(appString(R.string.report_app_version, appVersion))
        if (device != null) {
            appendLine()
            appendLine(appString(R.string.report_heading_device))
            appendLine(device)
        }
    }.trimEnd()

    fun url(includeBody: Boolean = true): String {
        val heading = encode("[${kind.title}] ${title.trim()}")
        val bodyQuery = if (includeBody) "&body=${encode(body())}" else ""
        return "$ISSUES_URL/new?title=$heading$bodyQuery"
    }

    // Long reports go through the clipboard intact rather than being truncated.
    val needsClipboard: Boolean get() = url().length > 7_000

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
