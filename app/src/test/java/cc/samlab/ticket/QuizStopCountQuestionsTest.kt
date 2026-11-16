package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.DailyQuizGenerator
import cc.samlab.ticket.ui.quiz.QUIZ_STOP_EXACT
import cc.samlab.ticket.ui.quiz.QUIZ_STOP_EXTREME
import cc.samlab.ticket.ui.quiz.QuizSchedule
import cc.samlab.ticket.ui.quiz.QuizStop
import cc.samlab.ticket.ui.quiz.QuizTrain
import cc.samlab.ticket.ui.quiz.quizStopCountQuestions
import cc.samlab.ticket.ui.quiz.quizTemplates
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizStopCountQuestionsTest {
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun dailyQuestionsUseOnlyRunningTrainsAndHaveRegisteredTemplates() {
        val fixture = dailyQuizTestData()
        val extraTrains = fixture.trains.take(40).flatMapIndexed { index, train ->
            val stops = train.stops.filterIndexed { position, _ -> position != 1 }
            listOf(
                train.copy(code = "G${8000 + index}", stops = stops),
                train.copy(
                    code = "G${9000 + index}", stops = stops,
                    schedule = QuizSchedule(date.minusDays(1), date.minusDays(1), 0, 0),
                ),
            )
        }
        val sources = fixture.copy(trains = fixture.trains + extraTrains)
        val trains = sources.trains.associateBy { it.code }
        val questions = DailyQuizGenerator(sources).generate(date)
        val stopQuestions = questions.filter { it.category in QUIZ_STOP_EXTREME..QUIZ_STOP_EXACT }
        assertEquals(setOf(QUIZ_STOP_EXTREME, QUIZ_STOP_EXACT), stopQuestions.map { it.category }.toSet())
        for (question in stopQuestions) {
            val options = question.options.map { trains.getValue(it.label) }
            assertTrue(options.all { it.schedule.runs(date) })
            assertEquals(options.map { it.choice() }, question.options)
            assertTrue(question.title.resource in quizTemplates.values)
            assertTrue(question.statement.resource in quizTemplates.values)
        }
    }

    @Test fun exactCountIncludesBothLimitsAndComparisonAnswersAreUnique() {
        val schedule = QuizSchedule(date, date, 0, 0)
        val trains = listOf(2, 3, 47, 48, 49, 50).flatMap { count ->
            (0..39).map { sequence ->
                QuizTrain(
                    "G${count * 100 + sequence}",
                    List(count) { QuizStop("站$it", null, null, null) },
                    schedule,
                )
            }
        }
        val countsByCode = trains.associate { it.code to it.stops.size }
        val questions = quizStopCountQuestions(trains, date, date.toString()) { true }
        val prompts = questions.groupBy { it.title.resource }
        assertEquals(
            setOf(R.string.quiz_q_stop_count_most, R.string.quiz_q_stop_count_fewest, R.string.quiz_q_stop_count_exact),
            prompts.keys,
        )
        assertEquals(
            setOf("2", "3", "47", "48", "49"),
            prompts.getValue(R.string.quiz_q_stop_count_exact).map { it.title.arguments.single() }.toSet(),
        )
        for (question in questions) {
            assertEquals(4, question.options.map { it.label }.toSet().size)
            val counts = question.options.map { countsByCode.getValue(it.label) }
            val correct = counts[question.correctIndex]
            val expected = when (question.title.resource) {
                R.string.quiz_q_stop_count_most -> counts.max()
                R.string.quiz_q_stop_count_fewest -> counts.min()
                else -> question.title.arguments.single().toInt()
            }
            assertEquals(expected, correct)
            assertEquals(1, counts.count { it == expected })
            assertTrue(counts.max() - counts.min() <= (correct / 5).coerceIn(1, 3))
        }
    }
}
