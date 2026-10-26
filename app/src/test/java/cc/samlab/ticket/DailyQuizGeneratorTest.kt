package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DailyQuizGeneratorTest {
    private val sources = dailyQuizTestData()
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun sameDateIsIdenticalAcrossLocalesAndGeneratorInstances() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val first = DailyQuizGenerator(sources).generate(date)
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(first, DailyQuizGenerator(sources).generate(date))
            assertEquals(20, first.size)
            assertEquals(20, first.map { it.id }.distinct().size)
            assertTrue(first.all { it.options.size == 4 && it.correctIndex in 0..3 })
            assertTrue(first.all { it.options.map { choice -> choice.label }.distinct().size == 4 })
            assertTrue(first.any { it.category == 10 })
            assertTrue(first.any { it.category == 8 })
        } finally { Locale.setDefault(previous) }
    }

    @Test fun noQuestionRepeatsInAnyRolling31DaysAcrossMonthAndYearBoundaries() {
        val seen = mutableMapOf<String, LocalDate>()
        val start = LocalDate.of(2026, 12, 10)
        repeat(64) { offset ->
            val day = start.plusDays(offset.toLong())
            val questions = DailyQuizGenerator(sources).generate(day)
            assertEquals(20, questions.size)
            for (question in questions) {
                assertTrue(quizEligible(question.id, day))
                seen[question.id]?.let { old ->
                    assertTrue("Repeated ${question.id}: $old / $day", day.toEpochDay() - old.toEpochDay() >= 32)
                }
                seen[question.id] = day
            }
        }
    }

    @Test fun semanticCooldownSurvivesChangingSourceVersions() {
        val id = "routing-next:G1"
        val eligible = (0..31).map { date.plusDays(it.toLong()) }.single { quizEligible(id, it) }
        for (offset in 1..31) assertFalse(quizEligible(id, eligible.plusDays(offset.toLong())))
        assertTrue(quizEligible(id, eligible.plusDays(32)))
    }

    @Test fun comparisonQuestionsHaveUniqueNumericalAnswers() {
        val index = sources.trains.associateBy { it.code }
        val questions = DailyQuizGenerator(sources).generate(date)
        for (question in questions.filter { it.category == 2 }) {
            val metric = when {
                question.id.contains("DISTANCE") -> QuizMetric.DISTANCE
                question.id.contains("DURATION") -> QuizMetric.DURATION
                else -> QuizMetric.SPEED
            }
            val values = question.options.map { metric.value(index.getValue(it.label))!! }
            assertEquals(values.max(), values[question.correctIndex], 0.0001)
            assertEquals(1, values.count { it == values.max() })
        }
    }
}
