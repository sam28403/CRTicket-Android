package cc.samlab.ticket

import cc.samlab.ticket.ui.report.ISSUES_URL
import cc.samlab.ticket.ui.report.IssueReport
import cc.samlab.ticket.ui.report.ReportKind
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportTest : StringResourceTest() {
    private fun report(
        title: String = "车票 & 日期? #1",
        description: String = "中文说明\n第二行 + emoji 🚆",
        device: String? = null,
    ) = IssueReport(ReportKind.BUG, title, description, "", "", "0.6.3", device)

    @Test fun encodesChineseNewlinesAndReservedCharactersWithoutChangingContent() {
        val report = report()
        val url = report.url()
        assertTrue(url.startsWith("$ISSUES_URL/new?"))
        val query = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals("[错误报告] ${report.title}", query["title"])
        assertEquals(report.body(), query["body"])
        assertFalse(report.needsClipboard)
    }

    @Test fun deviceInformationAndOptionalSectionsAreIncludedOnlyWhenProvided() {
        assertFalse(report().body().contains("### 设备信息"))
        assertFalse(report().body().contains("### 复现步骤"))
        assertTrue(report(device = "Test phone\nAndroid 16").body().contains("Test phone\nAndroid 16"))
        val full = report().copy(steps = "1. 打开车票", expected = "正确显示日期")
        assertTrue(full.body().contains("### 复现步骤\n1. 打开车票"))
        assertTrue(full.body().contains("### 预期表现\n正确显示日期"))
    }

    @Test fun longReportsPreserveFullTextAndUseTitleOnlyUrl() {
        val report = report(description = "问题详情".repeat(3000))
        assertTrue(report.needsClipboard)
        assertTrue(report.body().contains(report.description))
        assertFalse(report.url(includeBody = false).contains("&body="))
    }

    @Test fun blankRequiredFieldsCannotBeSubmitted() {
        assertFalse(report(title = "  ").ready)
        assertFalse(report(description = "\n ").ready)
        assertTrue(report().ready)
    }
}
