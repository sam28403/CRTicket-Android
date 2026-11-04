package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.quiz.*
import cc.samlab.ticket.ui.station.NativeStationBoardController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuizAnnualLiveDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = quizToday()
    private val snapshots = linkedMapOf<File, ByteArray?>()
    private lateinit var welcome: WelcomeTestState
    private var notice: Boolean? = null
    private var fixture: TabletPageFixture? = null

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", 0)
        notice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        for (date in listOf(day, day.minusDays(365))) {
            val file = File(context.filesDir, "daily-quiz/$date.json")
            snapshots[file] = file.takeIf { it.exists() }?.readBytes()
            file.delete()
        }
    }

    @After fun restore() {
        fixture?.close()
        snapshots.forEach { (file, content) ->
            content?.let { file.parentFile!!.mkdirs(); file.writeBytes(it) } ?: file.delete()
        }
        context.getSharedPreferences("android", 0).edit().apply {
            notice?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
        }.commit()
        welcome.close()
    }

    private fun page(): TabletPageFixture = TabletPageFixture(compose).also { fixture = it }

    private fun repository(page: TabletPageFixture): DailyQuizRepository {
        val getter = MainActivity::class.java.getDeclaredMethod("getStationBoard")
        getter.isAccessible = true
        val station = getter.invoke(page.activity) as NativeStationBoardController
        return DailyQuizRepository(context) { station.quizCounts.query(it) }
    }

    private fun saved(): QuizSession = runBlocking {
        requireNotNull(DailyQuizRepository(context).saved(day))
    }

    private fun awaitQuestion() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("quiz-question-0").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun onlineQuizUsesFreshSourcesAndPreservesAnAnswerWhenReopened() {
        val page = page()
        page.navigate("/railfan/quiz")
        awaitQuestion()
        val original = saved()
        assertTrue(original.sourceVersion.startsWith("live-v1:"))
        assertEquals(20, original.questions.size)
        assertTrue(original.questions.none { it.category in setOf(5, 7) })
        val question = original.questions.first()
        val wrong = (question.correctIndex + 1) % 4
        compose.onNodeWithText(question.options[wrong].label).performScrollTo().performClick()
        compose.waitUntil(10_000) { saved().answers.first() == wrong }
        page.capture("quiz-annual-live-answer")
        page.navigate("/")
        page.navigate("/railfan/quiz")
        awaitQuestion()
        assertEquals(question, saved().questions.first())
        assertEquals(wrong, saved().answers.first())
        page.capture("quiz-annual-live-reopened")
    }

    @Test fun historyExactly365DaysOldExcludesIdsAndSimilarOptionsWithRealOnlineData() {
        val page = page()
        val repository = repository(page)
        runBlocking {
            val original = repository.load(day)
            val keys = quizSelectionKeys(original.questions)
            repository.save(original.copy(date = day.minusDays(365)))
            File(context.filesDir, "daily-quiz/$day.json").delete()
            val next = repository.load(day)
            assertEquals(20, next.questions.size)
            assertTrue(next.questions.all { quizSelectionKeys(it).none(keys::contains) })
            assertTrue(next.sourceVersion.startsWith("live-v1:"))
            println("365-day history filtered ${keys.size} fingerprints; fresh=${next.sourceVersion}")
        }
    }

    @Test fun failedOnlineBridgeNeverOpensOrOverwritesAHistoricalQuiz() {
        val sources = QuizSourceReader.load(context)
        val old = QuizSession(day, "historical-snapshot", DailyQuizGenerator(sources).generate(day))
        runBlocking { DailyQuizRepository(context).save(old) }
        val bytes = File(context.filesDir, "daily-quiz/$day.json").readBytes()
        val page = page()
        // Inject a transport failure only for quiz requests; do not change device networking.
        page.evaluate("""
            window.addEventListener('crticket-visited-stations-request', event => {
                if (!event.detail?.quizDate) return;
                event.stopImmediatePropagation();
                CRTicketAndroid.stationBoardResult(JSON.stringify({
                    kind: 'quizCounts', requestId: event.detail.requestId, error: true
                }));
            }, true);
        """.trimIndent())
        page.navigate("/railfan/quiz")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(context.getString(R.string.quiz_error)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quiz-question-0").assertDoesNotExist()
        assertArrayEquals(bytes, File(context.filesDir, "daily-quiz/$day.json").readBytes())
        page.capture("quiz-annual-live-failure")
    }
}
