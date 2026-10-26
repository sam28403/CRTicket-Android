package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuizCompletionDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = quizToday()
    private val file = File(context.filesDir, "daily-quiz/$date.json")
    private var previous: ByteArray? = null
    private val repository = DailyQuizRepository(context)
    private val questions = List(20) { index ->
        QuizQuestion(
            "completion-$index", 1,
            QuizText(R.string.quiz_q_extreme, listOf("@quiz_north")),
            List(4) { QuizChoice("Station$it", QuizOptionKind.STATION) },
            0, QuizText(R.string.quiz_f_extreme, listOf("Station0", "@quiz_north")),
        )
    }

    @Before fun snapshot() { previous = file.takeIf { it.exists() }?.readBytes() }

    @After fun restore() {
        previous?.let { file.writeBytes(it) } ?: file.delete()
    }

    @Test fun lastCorrectAnswerCannotCompleteWithEarlierQuestionsSkipped() = runBlocking {
        repository.save(QuizSession(date, "fixture", questions, current = 19))
        val state = DailyQuizState(repository) { 12345L }
        state.load(date)
        state.answer(19, 0) { fail("Correct answer triggered rejection") }
        var remaining = 0
        state.move(1, expectedCorrect = questions.last().id) { remaining = it }
        assertEquals(19, remaining)
        assertEquals(19, state.session!!.current)
        assertFalse(state.session!!.completed)
        assertNull(state.pendingCorrect)
        assertEquals(state.session, repository.saved(date))
    }

    @Test fun oldCompletedSessionsWithSkippedQuestionsResumeAtFirstUnanswered() {
        val old = QuizSession(
            date, "fixture", questions, listOf(0) + List(19) { -1 },
            current = 20, completed = true,
        )
        val resumed = QuizSessionCodec.decode(QuizSessionCodec.encode(old), date)
        assertEquals(1, resumed.current)
        assertEquals(old.answers, resumed.answers)
        assertEquals(old.questions, resumed.questions)
        assertFalse(resumed.completed)
    }

    @Test fun resetClearsOnlyProgressAndIsRestrictedToCi() = runBlocking {
        val completed = QuizSession(
            date, "fixture", questions, List(20) { 0 }, 20, true, List(20) { 12345L },
        )
        repository.save(completed)
        val state = DailyQuizState(repository)
        state.load(date)
        state.reset()
        val expected = if (BuildConfig.BUILD_TYPE == "ci") completed.resetProgress() else completed
        assertEquals(expected, state.session)
        assertEquals(expected, repository.saved(date))
        if (BuildConfig.BUILD_TYPE == "ci") {
            assertEquals(20, expected.remaining)
            assertEquals(0, expected.current)
            assertTrue(expected.answerTimes.all { it == null })
            assertFalse(expected.completed)
        }
    }

    @Test fun fourReplacementsRememberEarlierSetsAcrossRepositoryReloads() = runBlocking {
        org.junit.Assume.assumeTrue(BuildConfig.BUILD_TYPE == "ci")
        val sources = QuizSourceReader.load(context)
        var current = QuizSession(date, sources.version, DailyQuizGenerator(sources).generate(date))
        var history = quizSelectionKeys(current.questions)
        repeat(4) {
            repository.save(current.copy(
                answers = current.questions.map { it.correctIndex }, current = 20, completed = true,
            ))
            current = repository.reset(repository.load(date), replaceQuestions = true)
            assertTrue(quizSelectionKeys(current.questions).none { key -> key in history })
            history = history + quizSelectionKeys(current.questions)
            assertTrue(current.seenKeys.containsAll(history))
            assertEquals(current, repository.load(date))
            assertEquals(0, current.current)
            assertEquals(20, current.remaining)
        }
        assertEquals(current.seenKeys, current.resetProgress().seenKeys)
    }
}
