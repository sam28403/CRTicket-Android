package cc.samlab.ticket

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.quiz.*
import cc.samlab.ticket.ui.station.NativeStationBoardController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuizStopCountDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun liveStopQuestionsShowTrainCardsAndKeepAnAnswerAfterReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val day = quizToday()
        val file = File(context.filesDir, "daily-quiz/$day.json")
        val oldSession = file.takeIf { it.exists() }?.readBytes()
        val welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", 0)
        val oldNotice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        file.delete()
        var page: TabletPageFixture? = null
        fun saved() = QuizSessionCodec.decode(
            android.util.AtomicFile(file).openRead().bufferedReader().use { it.readText() }, day,
        )
        try {
            val fixture = TabletPageFixture(compose)
            page = fixture
            fixture.navigate("/railfan/quiz")
            awaitQuestion(0, 120_000)
            val original = saved()
            assertTrue(original.sourceVersion.startsWith("live-v1:"))
            val controller = fixture.activity.testController<NativeStationBoardController>()
            val sources = requireNotNull(runBlocking { controller.quizCounts.query(day) }).sources
            assertEquals("live-v1:${sources.version}", original.sourceVersion)
            val trains = sources.trains.associateBy { it.code }
            val running = sources.trains.filter {
                it.stops.size >= 2 && Regex("^[A-Z]?\\d+$").matches(it.code) && it.schedule.runs(day)
            }
            val candidates = quizStopCountQuestions(running, day, day.toString()) { true }
            assertEquals(
                setOf(R.string.quiz_q_stop_count_most, R.string.quiz_q_stop_count_fewest, R.string.quiz_q_stop_count_exact),
                candidates.map { it.title.resource }.toSet(),
            )
            for (question in candidates) verifyQuestion(question, trains, day)

            val displayed = original.questions.withIndex().filter { it.value.category in QUIZ_STOP_EXTREME..QUIZ_STOP_EXACT }
            assertEquals(setOf(QUIZ_STOP_EXTREME, QUIZ_STOP_EXACT), displayed.map { it.value.category }.toSet())
            val answeredCategories = mutableSetOf<Int>()
            var position = 0
            for ((index, question) in displayed) {
                verifyQuestion(question, trains, day)
                while (position < index) {
                    compose.onNodeWithTag("quiz-next").performClick()
                    awaitQuestion(++position)
                }
                compose.onNodeWithText(question.title.resolve(fixture.activity)).assertIsDisplayed()
                question.options.forEachIndexed { option, choice ->
                    val card = hasAnyAncestor(hasTestTag("quiz-option-$option"))
                    compose.onNode(hasText(choice.label) and card, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed()
                    for (station in listOf(choice.from, choice.to).distinct()) {
                        compose.onNode(hasText(station) and card, useUnmergedTree = true).assertIsDisplayed()
                    }
                }
                compose.onNodeWithText(question.options.first().label).performScrollTo()
                fixture.capture("quiz-stop-count-${question.category}-$index")
                println("Question $index: ${question.title.resolve(fixture.activity)}; options " +
                    question.options.joinToString { "${it.label}=${trains.getValue(it.label).stops.size}" })
                if (answeredCategories.add(question.category)) {
                    val wrong = (question.correctIndex + 1) % 4
                    val card = hasTestTag("train-card:${question.options[wrong].label}") and
                        hasAnyAncestor(hasTestTag("quiz-option-$wrong"))
                    assertEquals(index, saved().current)
                    compose.onNode(card, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertIsEnabled()
                        .performTouchInput { click(Offset(width / 4f, center.y)) }
                    compose.waitForIdle()
                    compose.waitUntil(15_000) { saved().answers[index] == wrong }
                    fixture.navigate("/")
                    fixture.navigate("/railfan/quiz")
                    awaitQuestion(index, 120_000)
                    assertEquals(question, saved().questions[index])
                    assertEquals(wrong, saved().answers[index])
                    assertNotNull(saved().answerTimes[index])
                    compose.onNodeWithTag("quiz-option-$wrong").assertIsSelected()
                    fixture.capture("quiz-stop-count-${question.category}-reopened")
                }
            }
        } finally {
            page?.close()
            oldSession?.let { file.writeBytes(it) } ?: file.delete()
            preferences.edit().apply {
                oldNotice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) } ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
            }.commit()
            welcome.close()
        }
    }

    private fun awaitQuestion(index: Int, timeout: Long = 15_000) {
        compose.waitUntil(timeout) {
            compose.onAllNodesWithTag("quiz-question-$index").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun verifyQuestion(question: QuizQuestion, trains: Map<String, QuizTrain>, day: java.time.LocalDate) {
        assertEquals(4, question.options.map { it.label }.toSet().size)
        val options = question.options.map { trains.getValue(it.label) }
        assertTrue(options.all { it.schedule.runs(day) })
        assertEquals(options.map { it.choice() }, question.options)
        val counts = options.map { it.stops.size }
        val answer = counts[question.correctIndex]
        val expected = when (question.title.resource) {
            R.string.quiz_q_stop_count_most -> counts.max()
            R.string.quiz_q_stop_count_fewest -> counts.min()
            else -> question.title.arguments.single().toInt().also { assertTrue(it in 2..49) }
        }
        assertEquals(expected, answer)
        assertEquals(1, counts.count { it == expected })
        assertTrue(counts.max() - counts.min() <= (answer / 5).coerceIn(1, 3))
    }
}
