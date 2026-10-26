package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuizDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var welcome: WelcomeTestState
    private var previousNotice: Boolean? = null
    private lateinit var sessionFile: File
    private var previousSession: ByteArray? = null
    private lateinit var initial: QuizSession

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", 0)
        previousNotice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val day = quizToday()
        sessionFile = File(context.filesDir, "daily-quiz/$day.json")
        previousSession = sessionFile.takeIf { it.exists() }?.readBytes()
        runBlocking(Dispatchers.IO) {
            val sources = QuizSourceReader.load(context)
            initial = QuizSession(day, sources.version, DailyQuizGenerator(sources).generate(day))
            DailyQuizRepository(context).save(initial)
        }
    }

    @After fun restore() {
        previousSession?.let { sessionFile.writeBytes(it) } ?: sessionFile.delete()
        welcome.close()
        context.getSharedPreferences("android", 0).edit().apply {
            previousNotice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
        }.commit()
    }

    @Test fun dailyQuizRevealsColorsAdvancesCorrectAnswersAndReopensSummary() {
        var fixture: TabletPageFixture? = null
        try {
            val page = TabletPageFixture(compose)
            fixture = page
            page.evaluate("window.crticketNavigate('/railfan/quiz')")
            awaitTag("quiz-question-0")
            compose.onNodeWithTag("quiz-previous").assertIsNotEnabled()
            val wrong = (initial.questions[0].correctIndex + 1) % 4
            compose.onNodeWithText(initial.questions[0].options[wrong].label)
                .performScrollTo().performClick()
            compose.onNodeWithTag("quiz-option-$wrong").assertIsSelected()
            compose.onNodeWithTag("quiz-question-0").assertIsDisplayed()
            compose.onNodeWithTag("quiz-answer").assertDoesNotExist()
            page.capture("daily-quiz-wrong")

            compose.onNodeWithTag("quiz-next").performClick()
            val correct = initial.questions[1].correctIndex
            compose.onNodeWithText(initial.questions[1].options[correct].label)
                .performScrollTo().performClick()
            awaitTag("quiz-question-2")
            compose.onNodeWithTag("quiz-previous").performClick()
            compose.onNodeWithTag("quiz-option-$correct").assertIsSelected()
            compose.onNodeWithTag("quiz-question-1").assertIsDisplayed()
            repeat(18) { compose.onNodeWithTag("quiz-next").performClick() }
            compose.onNodeWithTag("quiz-next").performClick()
            compose.onNodeWithTag("quiz-summary").assertDoesNotExist()
            compose.onNodeWithTag("quiz-question-19").assertIsDisplayed()
            val unfinished = QuizSessionCodec.decode(sessionFile.readText(), initial.date)
            assertFalse(unfinished.completed)
            assertEquals(18, unfinished.remaining)
            page.capture("daily-quiz-unfinished")
            repeat(17) { compose.onNodeWithTag("quiz-previous").performClick() }
            for (index in 2..19) {
                val choice = (initial.questions[index].correctIndex + 1) % 4
                compose.onNodeWithText(initial.questions[index].options[choice].label)
                    .performScrollTo().performClick()
                compose.onNodeWithTag("quiz-next").performClick()
            }
            compose.onNodeWithTag("quiz-summary-result")
                .assertTextEquals(page.activity.getString(R.string.quiz_summary_result, 1, 20))
            compose.onNodeWithTag("quiz-summary-answer-0").assertIsDisplayed()
            compose.onNodeWithTag("quiz-summary-answer-1").assertDoesNotExist()
            compose.onNodeWithTag("quiz-done").assertIsDisplayed()
            if (BuildConfig.BUILD_TYPE == "ci") {
                compose.onNodeWithTag("quiz-reset").assertIsDisplayed()
                val resetBounds = compose.onNodeWithTag("quiz-reset").fetchSemanticsNode().boundsInRoot
                val doneBounds = compose.onNodeWithTag("quiz-done").fetchSemanticsNode().boundsInRoot
                assertTrue(resetBounds.bottom <= doneBounds.top)
            }
            page.capture("daily-quiz-summary")
            compose.onNodeWithTag("quiz-done").performClick()
            if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                awaitTag("navigation-page:/")
                page.capture("daily-quiz-complete")
            }
            page.close()
            fixture = null

            val reopened = TabletPageFixture(compose)
            fixture = reopened
            reopened.evaluate("window.crticketNavigate('/railfan/quiz')")
            awaitTag("quiz-summary")
            compose.onNodeWithTag("quiz-question-0").assertDoesNotExist()
            compose.onNodeWithTag("quiz-summary-result")
                .assertTextEquals(reopened.activity.getString(R.string.quiz_summary_result, 1, 20))
            compose.onNodeWithTag("quiz-done").assertIsDisplayed()
            val saved = QuizSessionCodec.decode(sessionFile.readText(), initial.date)
            assertTrue(saved.completed)
            assertNotNull(saved.answerTimes[0])
            assertNotNull(saved.answerTimes[1])
            if (BuildConfig.BUILD_TYPE == "ci") {
                compose.onNodeWithTag("quiz-reset").assertIsDisplayed().performClick()
                compose.onNodeWithTag("quiz-reset-cancel").performClick()
                compose.onNodeWithTag("quiz-summary").assertExists()
                compose.onNodeWithTag("quiz-reset").performClick()
                reopened.capture("daily-quiz-reset-dialog")
                compose.onNodeWithTag("quiz-reset-same").performClick()
                awaitTag("quiz-question-0")
                val reset = QuizSessionCodec.decode(sessionFile.readText(), initial.date)
                assertEquals(initial.questions, reset.questions)
                assertEquals(20, reset.remaining)
                assertTrue(reset.answerTimes.all { it == null })
                assertFalse(reset.completed)
                reopened.capture("daily-quiz-reset")
            } else compose.onNodeWithTag("quiz-reset").assertDoesNotExist()
        } finally { fixture?.close() }
    }

    @Test fun sourceSnapshotSupports32DaysWithoutRepeatedQuestions() {
        val page = TabletPageFixture(compose)
        try {
            val sources = QuizSourceReader.load(context)
            val seen = mutableSetOf<String>()
            val generator = DailyQuizGenerator(sources)
            repeat(32) { offset ->
                val questions = generator.generate(initial.date.plusDays(offset.toLong()))
                assertEquals(20, questions.size)
                for (question in questions) assertTrue("Repeated question: " + question.id, seen.add(question.id))
                InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                    putString("stream", "Verified daily quiz day ${offset + 1}/32\n")
                })
            }
            val today = generator.generate(initial.date)
            assertTrue(today.any { it.category == 5 })
            assertTrue(today.any { it.category == 8 })
            assertTrue(today.any { it.category == 10 })
            val encoded = QuizSessionCodec.encode(initial)
            assertEquals(initial, QuizSessionCodec.decode(encoded, initial.date))
            } finally { page.close() }
    }

    @Test fun ciCanReplaceTheCompletedSetAndResumeItAfterReopening() {
        org.junit.Assume.assumeTrue(BuildConfig.BUILD_TYPE == "ci")
        runBlocking {
            DailyQuizRepository(context).save(initial.copy(
                answers = List(20) { 0 }, current = 20, completed = true,
                answerTimes = List(20) { System.currentTimeMillis() },
            ))
        }
        var fixture: TabletPageFixture? = null
        try {
            val page = TabletPageFixture(compose)
            fixture = page
            page.evaluate("window.crticketNavigate('/railfan/quiz')")
            awaitTag("quiz-summary")
            compose.onNodeWithTag("quiz-reset").performClick()
            compose.onNodeWithTag("quiz-reset-new").performClick()
            awaitTag("quiz-question-0")
            val replaced = QuizSessionCodec.decode(sessionFile.readText(), initial.date)
            assertEquals(initial.date, replaced.date)
            assertEquals(20, replaced.remaining)
            assertEquals(20, replaced.questions.map { it.id }.distinct().size)
            assertTrue(replaced.questions.none { question -> initial.questions.any { it.id == question.id } })
            assertTrue(replaced.answerTimes.all { it == null })
            assertFalse(replaced.completed)
            page.capture("daily-quiz-new-set")
            page.close()
            fixture = null
            val reopened = TabletPageFixture(compose)
            fixture = reopened
            reopened.evaluate("window.crticketNavigate('/railfan/quiz')")
            awaitTag("quiz-question-0")
            assertEquals(replaced, QuizSessionCodec.decode(sessionFile.readText(), initial.date))
        } finally { fixture?.close() }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
}

