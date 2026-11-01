package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizPassengerStationsTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test fun retainsWeeklyStationsAndRejectsStationsWithoutService() {
        val sources = QuizSources("test", emptyList(), listOf(
            train("马海", QuizSchedule(start, start.plusDays(30), 7, 2)),
            train("额济纳", QuizSchedule(start, start.plusDays(30), 7, 8)),
            train("不开行", QuizSchedule(start, start.plusDays(30), 7, 0)),
            train("无日期", QuizSchedule(null, null, 0, 0)),
            train("窗口内无车", QuizSchedule(start, start, 7, 2)),
        ), emptyList(), emptyList())
        assertEquals(setOf("马海", "额济纳"), sources.passengerStationNames())
        assertFalse(sources.trains.first().schedule.runs(start))
    }

    @Test fun excludesUnusedCatalogStationsFromEveryGeneratedQuestion() {
        val original = dailyQuizTestData()
        val abandoned = QuizStation("废弃站", 110.0, 30.0, "City0")
        val sources = original.copy(stations = original.stations + abandoned)
        for (offset in 0L..31L) {
            val questions = DailyQuizGenerator(sources).generate(start.plusDays(offset))
            assertEquals(20, questions.size)
            assertTrue(questions.none { it.usesExcludedStation(setOf(abandoned.name)) })
        }
    }

    private fun train(station: String, schedule: QuizSchedule) = QuizTrain(
        "K1", listOf(QuizStop(station, null, 600, 0.0)), schedule,
    )

    @Test fun repairsSavedQuestionsWithoutErasingOtherAnswers() {
        val original = dailyQuizTestData()
        val sources = original.copy(stations = original.stations + QuizStation("废弃站", 110.0, 30.0))
        val valid = DailyQuizGenerator(sources).generate(start)
        val invalid = valid[0].copy(options = valid[0].options.map {
            it.copy(label = "废弃站", kind = QuizOptionKind.STATION)
        })
        val session = QuizSession(
            start, sources.version, listOf(invalid, valid[1]),
            answers = listOf(0, 1), completed = true,
            answerTimes = listOf(100L, 200L),
        )
        val repaired = repairQuizPassengerStations(session, sources) { valid.drop(2) }
        assertEquals(listOf(-1, 1), repaired.answers)
        assertEquals(listOf(null, 200L), repaired.answerTimes)
        assertEquals(valid[1], repaired.questions[1])
        assertFalse(repaired.completed)
        assertEquals(0, repaired.current)
        assertTrue(repaired.questions.none { it.usesExcludedStation(setOf("废弃站")) })
    }
}
