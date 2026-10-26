package cc.samlab.ticket

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class QuizReportDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var welcome: WelcomeTestState
    private lateinit var session: QuizSession
    private lateinit var sessionFile: File
    private var previous: ByteArray? = null
    private var previousNotice: Boolean? = null

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        val prefs = context.getSharedPreferences("android", 0)
        previousNotice = prefs.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        prefs.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val date = quizToday()
        sessionFile = File(context.filesDir, "daily-quiz/$date.json")
        previous = sessionFile.takeIf { it.exists() }?.readBytes()
        val question = QuizQuestion("report-fixture", 1,
            QuizText(R.string.quiz_q_extreme, listOf("@quiz_north")),
            listOf("北京南", "上海虹桥", "广州南", "杭州东").map { QuizChoice(it, QuizOptionKind.STATION) },
            0, QuizText(R.string.quiz_f_extreme, listOf("北京南", "@quiz_north")))
        val time = date.atTime(12, 34, 56).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        session = QuizSession(date, "fixture", List(20) { question.copy(id = "report-fixture-$it") },
            listOf(1, 0) + List(18) { 0 }, 20, true,
            listOf(time, time + 1000) + List(18) { null })
        runBlocking { DailyQuizRepository(context).save(session) }
    }

    @After fun restore() {
        previous?.let { sessionFile.writeBytes(it) } ?: sessionFile.delete()
        welcome.close()
        context.getSharedPreferences("android", 0).edit().apply {
            previousNotice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
        }.commit()
    }

    @Test fun summaryOpensThirdLevelReportAndCopiesCompleteTemplate() {
        val page = TabletPageFixture(compose)
        try {
            page.evaluate("window.crticketNavigate('/railfan/quiz')")
            await("quiz-summary")
            compose.onNodeWithTag("quiz-report-1").assertDoesNotExist()
            page.capture("quiz-report-summary")
            compose.onNodeWithTag("quiz-report-0").performClick()
            await("quiz-report-reason")
            compose.onNodeWithTag("quiz-report-open").assertIsNotEnabled()
            compose.onNodeWithTag("quiz-report-copy").performClick()
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            fun clip() = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(clip().contains("A. 北京南"))
            assertTrue(clip().contains("B. 上海虹桥"))
            assertTrue(clip().contains(quizReportTime(session.answerTimes[0]!!)))
            assertTrue(clip().contains(context.getString(R.string.quiz_report_reason_hint)))
            compose.onNodeWithTag("quiz-report-reason").performScrollTo().performTextInput("需要核对站点坐标。")
            closeSoftKeyboard()
            compose.onNodeWithTag("quiz-report-open").assertIsEnabled()
            compose.onNodeWithTag("quiz-report-copy").performClick()
            assertTrue(clip().contains("需要核对站点坐标。"))
            page.capture("quiz-report-form")
            compose.onNodeWithContentDescription(page.activity.getString(R.string.about_back)).performClick()
            await("quiz-summary")
            compose.onNodeWithTag("quiz-done").assertIsDisplayed()
            page.evaluate("window.crticketNavigate('/railfan/quiz/report?date=${session.date}&question=1')")
            compose.onNodeWithText(page.activity.getString(R.string.quiz_report_unavailable)).assertIsDisplayed()
            compose.onNodeWithTag("quiz-report-copy").assertDoesNotExist()
        } finally { page.close() }
    }

    @Test fun timestampRoundTripAndLegacyProgressRemainReadable() {
        val encoded = QuizSessionCodec.encode(session)
        assertEquals(session, QuizSessionCodec.decode(encoded, session.date))
        val legacy = JSONObject(encoded).apply { remove("answerTimes") }.toString()
        val old = QuizSessionCodec.decode(legacy, session.date)
        assertEquals(session.answers, old.answers)
        assertTrue(old.answerTimes.all { it == null })
        val report = quizIssueReport(context, old, 0, "需要核对。")
        assertEquals(context.getString(R.string.quiz_report_time_unknown, session.date.toString()), report.answeredAt)
    }

    private fun await(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
}
