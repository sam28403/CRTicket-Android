package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class QuizNearestTrainQuestionsTest {
    private val date = LocalDate.of(2026, 10, 27)
    private val schedule = QuizSchedule(date.minusYears(1), date.plusYears(1), 0, 0)

    @Test fun everyHundredKilometresThroughFiveThousandUsesOnlyTwentyPercentCandidates() {
        for (target in 100..5000 step 100) verifyTarget(QuizMetric.DISTANCE, target, target / 5)
    }

    @Test fun everyHourThroughFiftySevenUsesOnlyTenPercentCandidates() {
        for (hour in 1..57) verifyTarget(QuizMetric.DURATION, hour * 60, hour * 6)
    }

    @Test fun sparseAndFarAwayCandidatesDoNotCreateMisleadingQuestions() {
        val short = List(12) { train("G${it + 1}", 290.0 + it, 120 + it) }
        val questions = quizNearestTrainQuestions(short, date)
        assertTrue(questions.none { it.id.startsWith("train-nearest:DISTANCE:2000:") })
        assertTrue(questions.none { it.id.startsWith("train-nearest:DURATION:1440:") })
        val sparse = short.take(3) + train("G99", 5000.0, 3420)
        assertTrue(quizNearestTrainQuestions(sparse, date).isEmpty())
    }

    private fun verifyTarget(metric: QuizMetric, target: Int, window: Int) {
        val distances = listOf(
            target - window.toDouble(), target + window.toDouble(),
            target + 1.0, target + window / 2.0,
            target - window - 1.0, target + window + 1.0,
        )
        val trains = distances.mapIndexed { index, value ->
            train("G${index + 1}",
                if (metric == QuizMetric.DISTANCE) value else 1000.0,
                if (metric == QuizMetric.DURATION) value.toInt() else 120)
        }
        val id = "train-nearest:${metric.name}:$target:G1,G2,G3,G4"
        val day = (0..31).map { date.plusDays(it.toLong()) }.single { quizEligible(id, it) }
        val question = quizNearestTrainQuestions(trains, day).distinctBy { it.id }.single { it.id == id }
        assertEquals(setOf("G1", "G2", "G3", "G4"), question.options.map { it.label }.toSet())
        val index = trains.associateBy { it.code }
        val gaps = question.options.map { abs(metric.value(index.getValue(it.label))!! - target) }
        assertTrue(gaps.all { it <= window })
        assertEquals(gaps.min(), gaps[question.correctIndex], 0.00001)
        assertEquals(1, gaps.count { it == gaps.min() })
        assertEquals(if (metric == QuizMetric.DISTANCE) "$target km" else "${target / 60} h",
            question.title.arguments.last())
    }

    private fun train(code: String, km: Double, minutes: Int) = QuizTrain(code, listOf(
        QuizStop("A", null, 0, 0.0),
        QuizStop("B", minutes, null, km),
    ), schedule)
}
