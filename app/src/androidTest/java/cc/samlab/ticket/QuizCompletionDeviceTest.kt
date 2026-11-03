package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    @Test fun selectionHistoryIncludingTravelTrainsSurvivesEncoding() {
        val session = QuizSession(
            date, "fixture", questions,
            seenKeys = setOf("q:123abc", "o:456def", "travel-train:G123"),
        )
        assertEquals(session, QuizSessionCodec.decode(QuizSessionCodec.encode(session), date))
    }

    @Test fun resetClearsOnlyProgressAndIsRestrictedToCi() = runBlocking {
        val completed = QuizSession(
            date, "fixture", questions, List(20) { 0 }, 20, true, List(20) { 12345L },
        )
        repository.save(completed)
        val state = DailyQuizState(repository)
        state.load(date)
        state.reset()
        val expected = if (BuildConfig.CI_BUILD) completed.resetProgress() else completed
        assertEquals(expected, state.session)
        assertEquals(expected, repository.saved(date))
        if (BuildConfig.CI_BUILD) {
            assertEquals(20, expected.remaining)
            assertEquals(0, expected.current)
            assertTrue(expected.answerTimes.all { it == null })
            assertFalse(expected.completed)
        }
    }

    @Test fun fourReplacementsRememberEarlierSetsAcrossRepositoryReloads() = runBlocking {
        org.junit.Assume.assumeTrue(BuildConfig.CI_BUILD)
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

    @Test fun leavingDuringReplacementStillPersistsNewQuestions() = runBlocking {
        org.junit.Assume.assumeTrue(BuildConfig.CI_BUILD)
        withTimeout(120_000) {
            val sources = QuizSourceReader.load(context)
            val original = QuizSession(
                date, sources.version, DailyQuizGenerator(sources).generate(date),
            )
            val completed = original.copy(
                answers = original.questions.map { it.correctIndex },
                current = 20,
                completed = true,
            )
            repository.save(completed)
            val generating = CompletableDeferred<Unit>()
            val continueGeneration = CompletableDeferred<Unit>()
            val slowRepository = DailyQuizRepository(context) {
                generating.complete(Unit)
                continueGeneration.await()
                emptyMap()
            }
            val reset = launch(Dispatchers.Default) {
                slowRepository.reset(completed, replaceQuestions = true)
            }
            try {
                generating.await()
                // Leaving QuizScreen cancels its rememberCoroutineScope.
                reset.cancel()
                val reopened = async(Dispatchers.Default) {
                    DailyQuizRepository(context).load(date)
                }
                continueGeneration.complete(Unit)
                reset.join()
                val restored = reopened.await()
                assertNotEquals(original.questions, restored.questions)
                assertTrue(restored.questions.none { next ->
                    original.questions.any { it.id == next.id }
                })
                assertEquals(0, restored.current)
                assertEquals(20, restored.remaining)
                assertFalse(restored.completed)
                assertEquals(restored, DailyQuizRepository(context).saved(date))
            } finally {
                continueGeneration.complete(Unit)
                reset.cancel()
                reset.join()
            }
        }
    }
}
