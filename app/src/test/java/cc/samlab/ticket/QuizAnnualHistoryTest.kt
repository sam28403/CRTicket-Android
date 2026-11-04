package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizAnnualHistoryTest {
    private val date = LocalDate.of(2028, 1, 5)

    @Test fun historyIncludes365PriorDaysAcrossLeapYearAndExcludesTodayAndOlderDates() {
        val dates = quizHistoryDates(date)
        assertEquals(365, dates.distinct().size)
        assertTrue(date.minusDays(365) in dates)
        assertTrue(date.minusDays(32) in dates)
        assertFalse(date in dates)
        assertFalse(date.minusDays(366) in dates)
    }

    @Test fun questionsFromAnEarlierSlotCycleAndSimilarOptionsAreExcluded() {
        val sources = dailyQuizTestData()
        val generator = DailyQuizGenerator(sources)
        val previous = generator.generate(date.minusDays(352))
        val history = quizSelectionKeys(previous)
        val next = generator.generate(date, seenKeys = history)
        assertEquals(20, next.size)
        assertTrue(next.all { question -> quizSelectionKeys(question).none { it in history } })
    }

    @Test fun liveSourcesRejectWrongDateAndNeverReuseAssetTrainsRoutesOrLines() {
        val catalog = dailyQuizTestData()
        val fresh = catalog.copy(version = "fresh", trains = catalog.trains.take(1), routes = emptyList())
        val live = QuizLiveData(date, fresh, mapOf("Station0" to 1))
        val sources = live.verifiedSources(catalog, date)
        assertEquals(fresh.trains, sources.trains)
        assertTrue(sources.routes.isEmpty())
        assertTrue(sources.lines.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            live.verifiedSources(catalog, date.plusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            live.copy(stationCounts = emptyMap()).verifiedSources(catalog, date)
        }
    }

    @Test fun refreshedQuestionsKeepOnlyAnswersWhoseFullEvidenceIsUnchanged() {
        val questions = DailyQuizGenerator(dailyQuizTestData()).generate(date)
        val old = QuizSession(date, "old", questions, answers = List(20) { 0 },
            current = 20, completed = true, answerTimes = List(20) { 123L })
        val changed = questions.toMutableList()
        changed[3] = changed[3].copy(correctIndex = (changed[3].correctIndex + 1) % 4)
        val refreshed = quizRefreshSession(old, date, "fresh", changed)
        assertEquals(-1, refreshed.answers[3])
        assertNull(refreshed.answerTimes[3])
        assertEquals(0, refreshed.answers[2])
        assertEquals(123L, refreshed.answerTimes[2])
        assertEquals(3, refreshed.current)
        assertFalse(refreshed.completed)
        assertTrue(refreshed.seenKeys.containsAll(quizSelectionKeys(questions)))
    }
}
