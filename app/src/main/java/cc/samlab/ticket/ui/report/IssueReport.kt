package cc.samlab.ticket.ui.report

import java.net.URLEncoder

internal const val ISSUES_URL = "https://github.com/sam28403/CRTicket/issues"

internal enum class ReportKind(val title: String) {
    BUG("错误报告"),
    FEATURE("功能建议"),
    QUESTION("使用问题"),
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
        appendLine("### 类型")
        appendLine(kind.title)
        appendLine()
        appendLine("### 问题描述")
        appendLine(description.trim())
        if (steps.isNotBlank()) {
            appendLine()
            appendLine("### 复现步骤")
            appendLine(steps.trim())
        }
        if (expected.isNotBlank()) {
            appendLine()
            appendLine("### 预期表现")
            appendLine(expected.trim())
        }
        appendLine()
        appendLine("### 应用版本")
        appendLine("CRTicket $appVersion")
        if (device != null) {
            appendLine()
            appendLine("### 设备信息")
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
