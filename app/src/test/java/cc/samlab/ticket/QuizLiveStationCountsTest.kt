package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizLiveStationCountsTest {
    private val date = LocalDate.of(2026, 10, 30)
    private val names = listOf("南宁", "万州北", "惠阳", "桂林北")
    private val counts = names.zip(listOf(167, 159, 157, 164)).toMap()
    private val old = QuizQuestion(
        "station-count:true:万州北,南宁,惠阳,桂林北", 3,
        QuizText(R.string.quiz_q_station_most),
        names.map { QuizChoice(it, QuizOptionKind.STATION) }, 3,
        QuizText(R.string.quiz_f_station_count, listOf("桂林北", "162")),
    )

    @Test fun liveCountsRepairSavedAnswerWithoutChangingChoicesOrProgress() {
        val session = QuizSession(date, "old", listOf(old), answers = listOf(0),
            current = 1, completed = true, answerTimes = listOf(123L))
        val repaired = repairQuizStationCounts(session, counts) { error("No replacement needed") }
        assertEquals("南宁", repaired.questions.single().options[repaired.questions.single().correctIndex].label)
        assertEquals(listOf("南宁", "167"), repaired.questions.single().statement.arguments)
        assertEquals(session.answers, repaired.answers)
        assertEquals(session.questions.single().options, repaired.questions.single().options)
        assertEquals(session.answerTimes, repaired.answerTimes)
        assertTrue(repaired.completed)
        val least = repairQuizStationCounts(session.copy(questions = listOf(old.copy(
            id = old.id.replace("true", "false")))), counts) { error("No replacement needed") }
        assertEquals(2, least.questions.single().correctIndex)
    }

    @Test fun unavailableLiveCountsReplaceOldCountQuestionAndClearOnlyItsAnswer() {
        val replacement = old.copy(id = "other", category = 1)
        val session = QuizSession(date, "old", listOf(old), answers = listOf(0),
            current = 1, completed = true, answerTimes = listOf(123L))
        val repaired = repairQuizStationCounts(session, emptyMap()) { listOf(replacement) }
        assertEquals(listOf(replacement), repaired.questions)
        assertEquals(listOf(-1), repaired.answers)
        assertEquals(listOf(null), repaired.answerTimes)
        assertFalse(repaired.completed)
        assertEquals(0, repaired.current)
    }

    @Test fun generatorNeverFallsBackToBundledCountsWhenLiveDataIsAbsent() {
        val result = DailyQuizGenerator(dailyQuizTestData()).generate(date)
        assertEquals(20, result.size)
        assertTrue(result.none { it.category == 3 })
    }
}
