package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizSelectionHistoryTest {
    private val sources = dailyQuizTestData()
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun eightSuccessiveSetsNeverReturnToEarlierQuestionsOrSimilarOptionGroups() {
        val generator = DailyQuizGenerator(sources)
        var history = quizSelectionKeys(generator.generate(date))
        val seen = mutableSetOf<String>()
        repeat(8) { index ->
            val questions = generator.generate(date, seenKeys = history, variationSeed = "reset-$index")
            assertEquals(20, questions.size)
            val withinSet = mutableSetOf<String>()
            for (question in questions) {
                assertTrue("Repeated question on reset $index", seen.add(question.id))
                val keys = quizSelectionKeys(question)
                assertTrue("History overlap on reset $index", keys.none { it in history })
                assertTrue("Similar options in one set", keys.none { it in withinSet })
                withinSet.addAll(keys)
            }
            history = history + withinSet
        }
    }

    @Test fun independentSeedsChangeSelectionWhileOrdinaryDailySelectionStaysStable() {
        val generator = DailyQuizGenerator(sources)
        val ordinary = generator.generate(date)
        val history = quizSelectionKeys(ordinary)
        val first = generator.generate(date, seenKeys = history, variationSeed = "first")
        assertEquals(first, generator.generate(date, seenKeys = history, variationSeed = "first"))
        assertNotEquals(first, generator.generate(date, seenKeys = history, variationSeed = "second"))
        assertEquals(ordinary, generator.generate(date))
    }

    @Test fun changingDirectionOrOneOfFourOptionsDoesNotBypassSimilarityFiltering() {
        fun question(id: String, names: List<String>) = QuizQuestion(
            id, 1, QuizText(R.string.quiz_q_extreme),
            names.map { QuizChoice(it, QuizOptionKind.STATION) }, 0,
            QuizText(R.string.quiz_f_extreme),
        )
        val original = quizSelectionKeys(question("north", listOf("A", "B", "C", "D")))
        assertTrue(quizSelectionKeys(question("south", listOf("D", "C", "B", "A"))).any { it in original })
        assertTrue(quizSelectionKeys(question("east", listOf("A", "B", "C", "E"))).any { it in original })
        assertTrue(quizSelectionKeys(question("west", listOf("A", "B", "E", "F"))).none { it in original })
    }
}
