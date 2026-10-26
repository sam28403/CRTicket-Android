package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import cc.samlab.ticket.ui.report.ISSUES_URL
import cc.samlab.ticket.ui.navigation.WideNavigationPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate

class QuizIssueReportTest : StringResourceTest() {
    private fun report(reason: String = "坐标可能错误 & 请核对 + #1\n第二行") = QuizIssueReport(
        "下面哪个车站最靠北？\nA. 北京南\nB. 上海虹桥\nC. 广州南\nD. 杭州东",
        "A. 北京南\n北京南站的位置最靠北。", "B. 上海虹桥", reason,
        "2026-10-26 12:34:56 UTC+08:00",
    )

    @Test fun reportContainsAllFiveFieldsAndPreservesReservedCharacters() {
        val value = report()
        val body = value.body()
        assertTrue(body.startsWith("题目：${value.question}"))
        assertTrue(body.contains("参考答案：${value.reference}"))
        assertTrue(body.contains("我的答案：${value.answer}"))
        assertTrue(body.contains("理由：${value.reason}"))
        assertTrue(body.endsWith("答题日期时间：${value.answeredAt}"))
        val url = value.url()
        assertTrue(url.startsWith("$ISSUES_URL/new?"))
        val query = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals(body, query["body"])
        assertEquals(value.title, query["title"])
    }

    @Test fun blankReasonsKeepCopyableTemplateButCannotOpenSubmission() {
        assertFalse(report(" \n ").ready)
        assertTrue(report("").body().contains("【请填写理由】"))
        assertTrue(report("核对车站位置").ready)
    }

    @Test fun longReportsUseClipboardWithoutTruncatingAnyField() {
        val value = report("完整理由".repeat(4000))
        assertTrue(value.needsClipboard)
        assertTrue(value.body().contains(value.reason))
        assertFalse(value.url(includeBody = false).contains("&body="))
    }

    @Test fun answerTimestampUsesBeijingTimeAcrossMidnight() {
        assertEquals("2026-10-27 00:20:30 UTC+08:00",
            quizReportTime(Instant.parse("2026-10-26T16:20:30Z").toEpochMilli()))
    }

    @Test fun onlyIncorrectAnswersInCompletedSessionsCanBeReported() {
        val question = QuizQuestion("test", 1, QuizText(R.string.quiz_q_extreme),
            List(4) { QuizChoice("Station$it", QuizOptionKind.STATION) }, 0,
            QuizText(R.string.quiz_f_extreme))
        val session = QuizSession(LocalDate.of(2026, 10, 26), "fixture", List(20) { question },
            listOf(1, 0) + List(18) { -1 }, 20, true)
        assertTrue(quizCanReport(session, 0))
        assertFalse(quizCanReport(session, 1))
        assertFalse(quizCanReport(session, 2))
        assertFalse(quizCanReport(session, -1))
        assertFalse(quizCanReport(session, 20))
        assertFalse(quizCanReport(session.copy(completed = false), 0))
    }

    @Test fun reportIsAChildJourneyOnWideScreens() {
        assertFalse(WideNavigationPolicy.startsJourney("/railfan/quiz/report?date=2026-10-26&question=0"))
    }
}
