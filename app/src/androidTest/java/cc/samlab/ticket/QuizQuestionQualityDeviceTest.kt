package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class QuizQuestionQualityDeviceTest {
    @Test fun bundledSnapshotProducesPlausibleDurationsDistancesTravelAndCities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sources = QuizSourceReader.load(context)
        val date = quizToday()
        val trains = sources.trains.associateBy { it.code }
        val closest = quizTrainQuestions(sources.trains.filter { it.schedule.runs(date) }, date)
            .filter { it.id.startsWith("train-nearest:DURATION:") }
        assertTrue(closest.any { it.id.startsWith("train-nearest:DURATION:1440:") })
        for (question in closest) {
            val target = question.id.split(':')[2].toInt()
            assertTrue(question.options.all {
                abs(trains.getValue(it.label).duration!! - target) <= target * 0.10
            })
        }
        val distances = quizLineQuestions(sources, date).filter { it.category == 5 }
        val ranks = distances.map { question ->
            val value = question.options[question.correctIndex].label.substringBefore(' ').toInt()
            question.options.count { it.label.substringBefore(' ').toInt() < value }
        }.toSet()
        assertTrue("Distances still use a fixed numerical rank: $ranks", ranks.size > 1)

        val travel = quizTravelQuestions(sources.trains.filter { Regex("^[A-Z]?\\d+$").matches(it.code) }, date)
        assertTrue(travel.isNotEmpty())
        for (question in travel) {
            val (from, to) = question.title.arguments
            question.options.forEachIndexed { index, choice ->
                val train = trains.getValue(choice.label)
                assertTrue(train.stops.any { it.station == from })
                assertTrue(quizInteriorEndpoints(train, from, to))
                assertEquals(index == question.correctIndex, train.stops.any { it.station == to })
                assertEquals(index == question.correctIndex, quizCanTravel(train, from, to, date))
            }
        }
        assertTrue(travel.map { quizTrainDigits(it.options[it.correctIndex].label) }.distinct().size > 1)

        val stations = sources.stations.associateBy { it.name }
        assertEquals("杭州", stations.getValue("千岛湖").city)
        val cities = quizCityQuestions(sources.stations, date)
        assertTrue(cities.isNotEmpty())
        for (question in cities) {
            val city = question.title.arguments.single()
            assertTrue(question.options.none { it.label.contains(city) })
            assertEquals(1, question.options.count { stations.getValue(it.label).city == city })
            assertEquals(city, stations.getValue(question.options[question.correctIndex].label).city)
        }
        val questions = DailyQuizGenerator(sources).generate(date)
        assertEquals(20, questions.size)
        assertTrue(questions.any { it.category == 11 })
        val session = QuizSession(date, sources.version, questions)
        assertEquals(session, QuizSessionCodec.decode(QuizSessionCodec.encode(session), date))
    }
}
