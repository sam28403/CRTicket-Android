package cc.samlab.ticket

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.quiz.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuizLiveCountsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun reopeningSavedQuizRepairsStationAnswerFromOnlineCounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val day = quizToday()
        // This fixture reproduces the reported question on its reported date.
        org.junit.Assume.assumeTrue(day.toString() == "2026-10-30")
        val welcome = WelcomeTestState(context)
        val file = File(context.filesDir, "daily-quiz/$day.json")
        val original = file.takeIf { it.exists() }?.readBytes()
        var page: TabletPageFixture? = null
        try {
            val sources = QuizSourceReader.load(context)
            val questions = DailyQuizGenerator(sources).generate(day).toMutableList()
            val names = listOf("南宁", "万州北", "惠阳", "桂林北")
            questions[2] = QuizQuestion("station-count:true:万州北,南宁,惠阳,桂林北", 3,
                QuizText(R.string.quiz_q_station_most),
                names.map { QuizChoice(it, QuizOptionKind.STATION) }, 3,
                QuizText(R.string.quiz_f_station_count, listOf("桂林北", "162")))
            runBlocking { DailyQuizRepository(context).save(QuizSession(day, sources.version, questions)) }
            page = TabletPageFixture(compose)
            page.navigate("/railfan/quiz")
            compose.waitUntil(110_000) {
                val saved = runCatching { QuizSessionCodec.decode(file.readText(), day) }.getOrNull()
                saved?.questions?.get(2)?.statement?.arguments == listOf("南宁", "167")
            }
            val repaired = QuizSessionCodec.decode(file.readText(), day).questions[2]
            assertEquals(names, repaired.options.map { it.label })
            assertEquals(0, repaired.correctIndex)
        } finally {
            page?.close()
            original?.let { file.writeBytes(it) } ?: file.delete()
            welcome.close()
        }
    }
}
