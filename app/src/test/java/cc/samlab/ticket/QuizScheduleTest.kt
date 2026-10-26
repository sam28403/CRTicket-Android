package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import cc.samlab.ticket.ui.railfan.VehicleHistory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizScheduleTest {
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun missingExpiredAndAlternatingSchedulesAreNotTreatedAsDaily() {
        assertFalse(QuizSchedule(null, null, 0, 0).runs(date))
        val alternate = QuizSchedule(date, date.plusDays(3), 2, 1)
        assertTrue(alternate.runs(date))
        assertFalse(alternate.runs(date.plusDays(1)))
        assertTrue(alternate.runs(date.plusDays(2)))
        assertFalse(alternate.runs(date.plusDays(4)))
    }

    @Test fun stationCountsUseLocalDayAndDeduplicateMidnightStops() {
        val previous = date.minusDays(1)
        val train = QuizTrain("K1", listOf(
            QuizStop("A", null, 1380, 0.0),
            QuizStop("B", 1435, 1450, 100.0),
            QuizStop("C", 1500, null, 200.0),
        ), QuizSchedule(previous, previous, 0, 0))
        assertEquals(mapOf("B" to 1, "C" to 1), quizStationCounts(listOf(train), date))
        assertEquals(mapOf("A" to 1, "B" to 1), quizStationCounts(listOf(train), previous))
    }

    @Test fun travelAcceptsAnyDepartureTodayAndRejectsReverseOrder() {
        val train = QuizTrain("K1", listOf(
            QuizStop("A", null, 60, 0.0),
            QuizStop("B", 120, 130, 100.0),
            QuizStop("C", 1600, null, 200.0),
        ), QuizSchedule(date, date, 0, 0))
        assertTrue(quizCanTravel(train, "A", "C", date))
        assertTrue(quizCanTravel(train, "B", "C", date))
        assertFalse(quizCanTravel(train, "C", "A", date))
        assertFalse(quizCanTravel(train, "A", "C", date.plusDays(1)))
        val overnight = train.copy(stops = listOf(
            QuizStop("A", null, 1450, 0.0), QuizStop("C", 1600, null, 100.0),
        ), schedule = QuizSchedule(date.minusDays(1), date.minusDays(1), 0, 0))
        assertTrue(quizCanTravel(overnight, "A", "C", date))
    }

    @Test fun vehicleQuestionsRejectOldOrConflictingUnits() {
        val day = (0..31).map { date.plusDays(it.toLong()) }.first { quizEligible("vehicle-today:G1", it) }
        val other = listOf("G2", "G3", "G4").associateWith { train ->
            listOf(VehicleHistory("CR400AF${1000 + train.drop(1).toInt()}", train, "$day 08:00"))
        }
        val today = VehicleHistory("CR400AF1001", "G1", "$day 09:00")
        assertTrue(quizVehicleQuestions(other + ("G1" to listOf(today.copy(date = "${day.minusDays(1)} 09:00"))), day)
            .none { it.id == "vehicle-today:G1" })
        assertTrue(quizVehicleQuestions(other + ("G1" to listOf(today, today.copy(vehicle = "CR400AF9999"))), day)
            .none { it.id == "vehicle-today:G1" })
        val verified = quizVehicleQuestions(other + ("G1" to listOf(today)), day)
            .single { it.id == "vehicle-today:G1" }
        assertEquals("CR400AF-1001", verified.options[verified.correctIndex].label)
    }
}
