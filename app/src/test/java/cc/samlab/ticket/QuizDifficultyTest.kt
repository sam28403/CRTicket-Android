package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizDifficultyTest {
    private val sources = dailyQuizTestData()
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun geographicChoicesAreNearbyAndStillHaveUniqueDirections() {
        assertEquals(111.195, quizStationDistanceKm(
            QuizStation("A", 0.0, 0.0), QuizStation("B", 0.0, 1.0),
        ), 0.01)
        val stations = sources.stations.associateBy { it.name }
        val questions = quizGeography(sources.stations, date).filter { it.category == 1 }
        assertTrue(questions.isNotEmpty())
        for (question in questions) {
            val group = question.options.map { stations.getValue(it.label) }
            for (a in group) for (b in group) assertTrue(quizStationDistanceKm(a, b) <= 120.01)
        }
    }

    @Test fun stationCountsRejectObviousOutliers() {
        assertFalse(quizComparableCounts(listOf(5, 5, 5, 500)))
        assertFalse(quizComparableCounts(listOf(1, 2, 3, 4)))
        assertFalse(quizComparableCounts(listOf(100, 130, 140, 145)))
        assertTrue(quizComparableCounts(listOf(5, 6, 6, 7)))
        assertTrue(quizComparableCounts(listOf(490, 500, 510, 520)))
        val counts = (0 until 400).associate { "Station$it" to 20 + it / 5 }
        val questions = quizStationCountQuestions(counts, date)
        assertTrue(questions.isNotEmpty())
        for (question in questions) assertTrue(
            quizComparableCounts(question.options.map { counts.getValue(it.label) }),
        )
    }

    @Test fun routingDistractorsAllMatchTheAnswerTrainType() {
        val mixed = sources.copy(trains = sources.trains + sources.trains.take(100).mapIndexed { index, row ->
            row.copy(code = "D${index + 1}")
        })
        val questions = quizRoutingQuestions(mixed, date)
        assertTrue(questions.isNotEmpty())
        for (question in questions) assertEquals(setOf("G"), question.options.map { quizTrainType(it.label) }.toSet())
        assertEquals("numeric", quizTrainType("1461"))
    }

    @Test fun travelChoicesNeverUseEitherQueriedStationAsAnEndpoint() {
        val questions = quizTravelQuestions(sources.trains, date)
        val trains = sources.trains.associateBy { it.code }
        assertTrue(questions.isNotEmpty())
        for (question in questions) {
            val (from, to) = question.title.arguments
            question.options.forEachIndexed { index, option ->
                val train = trains.getValue(option.label)
                assertTrue(quizInteriorEndpoints(train, from, to))
                assertEquals(index == question.correctIndex, quizCanTravel(train, from, to, date))
            }
        }
    }

    @Test fun tabletCompletionAlwaysOpensTicketGeneration() {
        assertEquals("/", quizCompletionRoute(false, 600))
        assertEquals("/", quizCompletionRoute(true, 400))
        assertNull(quizCompletionRoute(false, 411))
    }
}
