package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuizTravelShuffleTest {
    private val sources = dailyQuizTestData()
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun changingSeedShufflesAllTrainsAndInteriorPairs() {
        val first = quizTravelAnchors(sources.trains, "first").toList()
        assertEquals(sources.trains.toSet(), first.toSet())
        assertEquals(first, quizTravelAnchors(sources.trains.reversed(), "first").toList())
        assertNotEquals(first.take(24), quizTravelAnchors(sources.trains, "second").take(24).toList())
        val train = sources.trains.first().copy(stops = List(12) {
            QuizStop("Station$it", it * 60, it * 60 + 5, it * 100.0)
        })
        val pairs = quizTravelPairs(train, "first")
        assertEquals(45, pairs.toSet().size)
        assertTrue(pairs.all { (from, to) -> from > 0 && to < train.stops.lastIndex && from < to })
        assertNotEquals(pairs, quizTravelPairs(train, "second"))
    }

    @Test fun successiveResetsDoNotReuseCorrectTravelTrains() {
        val generator = DailyQuizGenerator(sources)
        var history = emptySet<String>()
        val correctTrains = mutableSetOf<String>()
        repeat(4) { index ->
            val questions = generator.generate(date, seenKeys = history, variationSeed = "shuffle-$index")
            questions.filter { it.category == 10 }.forEach {
                assertTrue(correctTrains.add(it.options[it.correctIndex].label))
            }
            history = history + quizSelectionKeys(questions)
        }
        assertTrue(correctTrains.size >= 4)
    }
}
