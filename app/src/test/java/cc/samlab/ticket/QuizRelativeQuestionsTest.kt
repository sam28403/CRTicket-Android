package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizRelativeQuestionsTest {
    private val reference = QuizStation("广州", 113.26, 23.15)
    private val date = LocalDate.of(2026, 10, 30)

    private fun nearby(direction: QuizDirection): List<QuizStation> {
        val offsets = listOf(0.12, 0.16, -0.11, -0.14, -0.17, -0.19)
        return offsets.mapIndexed { index, offset ->
            val delta = offset * direction.sign
            if (direction in listOf(QuizDirection.NORTH, QuizDirection.SOUTH)) {
                reference.copy(name = "站$index", latitude = reference.latitude + delta)
            } else reference.copy(name = "站$index", longitude = reference.longitude + delta)
        }
    }

    @Test fun allDirectionsUseNearbyOptionsWithComparableAxisOffsetsAndDistances() {
        for (direction in QuizDirection.entries) {
            val choices = nearby(direction)
            val question = checkNotNull(quizRelativeQuestion(reference, choices, direction))
            val byName = choices.associateBy { it.name }
            val options = question.options.map { byName.getValue(it.label) }
            assertTrue(quizComparableRelativeChoices(reference, options, direction))
            val correct = options[question.correctIndex]
            assertTrue((direction.axis(correct) - direction.axis(reference)) * direction.sign > 0)
            for (index in options.indices.filter { it != question.correctIndex }) {
                assertTrue((direction.axis(options[index]) - direction.axis(reference)) * direction.sign < 0)
            }
            assertEquals(question, quizRelativeQuestion(reference, choices, direction))
        }
    }

    @Test fun hainanDistractorsAndOneHeilongjiangAnswerCannotFormAQuestion() {
        val distant = listOf(
            QuizStation("海南一", 110.3, 20.0), QuizStation("海南二", 110.0, 19.8),
            QuizStation("海南三", 109.9, 19.7), QuizStation("黑龙江", 126.6, 45.7),
        )
        assertFalse(quizComparableRelativeChoices(reference, distant, QuizDirection.NORTH))
        assertNull(quizRelativeQuestion(reference, distant, QuizDirection.NORTH))
    }

    @Test fun similarRadialDistancesStillRejectOneFarAxisOutlier() {
        val choices = listOf(
            reference.copy(name = "北", latitude = 23.55),
            reference.copy(name = "南一", longitude = 113.65, latitude = 23.12),
            reference.copy(name = "南二", longitude = 113.66, latitude = 23.11),
            reference.copy(name = "南三", longitude = 113.67, latitude = 23.10),
        )
        assertNull(quizRelativeQuestion(reference, choices, QuizDirection.NORTH))
    }

    @Test fun invalidSavedQuestionIsReplacedWithoutResettingOtherAnswers() {
        val choices = nearby(QuizDirection.NORTH)
        val sources = dailyQuizTestData().copy(stations = choices + reference)
        val valid = checkNotNull(quizRelativeQuestion(reference, choices, QuizDirection.NORTH))
        val invalid = valid.copy(options = valid.options.mapIndexed { i, option ->
            if (i == valid.correctIndex) option.copy(label = "黑龙江") else option
        })
        val other = valid.copy(id = "other", category = 1)
        val old = QuizSession(date, "test", listOf(invalid, other), listOf(0, 1),
            current = 2, completed = true, answerTimes = listOf(10L, 20L))
        val repaired = repairQuizRelativeQuestions(old, sources)
        assertEquals(valid, repaired.questions[0])
        assertEquals(listOf(-1, 1), repaired.answers)
        assertEquals(listOf(null, 20L), repaired.answerTimes)
        assertEquals(other, repaired.questions[1])
        assertFalse(repaired.completed)
        assertEquals(0, repaired.current)
        assertEquals(repaired, repairQuizRelativeQuestions(repaired, sources))
    }
}
