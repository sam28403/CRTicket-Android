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
        val closest = quizNearestTrainQuestions(sources.trains.filter { it.schedule.runs(date) }, date)
        assertTrue(closest.any { it.id.startsWith("train-nearest:DURATION:1440:") })
        assertTrue(closest.any { it.id.startsWith("train-nearest:DISTANCE:") })
        assertTrue(closest.any {
            it.id.startsWith("train-nearest:DISTANCE:") &&
                it.id.split(':')[2].toInt() !in listOf(100, 500, 1000, 2000)
        })
        assertTrue(closest.any {
            it.id.startsWith("train-nearest:DURATION:") &&
                it.id.split(':')[2].toInt() !in listOf(60, 120, 300, 600, 720, 1200, 1440)
        })
        for (question in closest) {
            val target = question.id.split(':')[2].toInt()
            val distance = question.id.startsWith("train-nearest:DISTANCE:")
            assertTrue(if (distance) target in 100..5000 && target % 100 == 0
                else target in 60..3420 && target % 60 == 0)
            val gaps = question.options.map {
                val train = trains.getValue(it.label)
                abs((if (distance) train.distance!! else train.duration!!.toDouble()) - target)
            }
            val window = target * (if (distance) 0.20 else 0.10)
            assertTrue(gaps.all { it <= window })
            assertEquals(gaps.min(), gaps[question.correctIndex], 0.00001)
            assertEquals(1, gaps.count { it == gaps.min() })
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
