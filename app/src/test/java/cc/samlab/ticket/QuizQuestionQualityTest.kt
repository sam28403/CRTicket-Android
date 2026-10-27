package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class QuizQuestionQualityTest {
    private val sources = dailyQuizTestData()
    private val date = LocalDate.of(2026, 10, 26)

    @Test fun nearestDurationChoicesAreAllNearTheRequestedDuration() {
        assertEquals(6, quizDurationWindow(60))
        assertEquals(12, quizDurationWindow(120))
        assertEquals(30, quizDurationWindow(300))
        assertEquals(60, quizDurationWindow(600))
        assertEquals(72, quizDurationWindow(720))
        assertEquals(120, quizDurationWindow(1200))
        assertEquals(144, quizDurationWindow(1440))
        val trains = sources.trains.associateBy { it.code }
        val questions = quizTrainQuestions(sources.trains, date)
            .filter { it.id.startsWith("train-nearest:DURATION:") }
        assertTrue(questions.any { it.id.startsWith("train-nearest:DURATION:1440:") })
        for (question in questions) {
            val target = question.id.split(':')[2].toInt()
            for (option in question.options) {
                assertTrue(abs(trains.getValue(option.label).duration!! - target) <= target * 0.10)
            }
        }
        val shortTrains = sources.trains.filter { it.duration!! in 120..180 }
        assertTrue(quizTrainQuestions(shortTrains, date).none { it.id.startsWith("train-nearest:DURATION:1440:") })
    }

    @Test fun correctDistanceUsesEveryNumericalRankAndNeverCreatesNonpositiveChoices() {
        val ranks = (0..127).map { index ->
            val offsets = quizDistanceOffsets("distance-$index", 1000, 50)
            assertEquals(3, offsets.distinct().size)
            assertTrue(offsets.all { 1000 + it * 50 > 0 && it != 0 })
            offsets.count { it < 0 }
        }.toSet()
        assertEquals(setOf(0, 1, 2, 3), ranks)
        for (index in 0..127) assertTrue(quizDistanceOffsets("short-$index", 30, 10).all { 30 + it * 10 > 0 })
    }

    @Test fun allTravelOptionsDepartAtOriginButOnlyTheReferenceTrainVisitsDestination() {
        val trains = sources.trains.associateBy { it.code }
        val questions = quizTravelQuestions(sources.trains, date)
        assertTrue(questions.isNotEmpty())
        for (question in questions) {
            val (from, to) = question.title.arguments
            question.options.forEachIndexed { index, option ->
                val train = trains.getValue(option.label)
                assertTrue(train.stops.any { it.station == from })
                assertEquals(index == question.correctIndex, train.stops.any { it.station == to })
                assertTrue(quizInteriorEndpoints(train, from, to))
                assertEquals(index == question.correctIndex, quizCanTravel(train, from, to, date))
            }
        }
        assertTrue(questions.map { quizTrainDigits(it.options[it.correctIndex].label) }.distinct().size > 1)
        assertTrue(questions.groupingBy { it.options[it.correctIndex].label }.eachCount().values.all { it <= 2 })
        val daily = DailyQuizGenerator(sources).generate(date).filter { it.category == 10 }
        assertEquals(2, daily.map { quizTrainDigits(it.options[it.correctIndex].label) }.distinct().size)
    }

    @Test fun cityQuestionDoesNotRevealTheCityInAnyOption() {
        val stations = listOf(
            QuizStation("千岛湖", 119.1, 29.7, "杭州"),
            QuizStation("杭州东", 120.2, 30.2, "杭州"),
            QuizStation("余姚北", 121.1, 30.1, "宁波"),
            QuizStation("昆山南", 120.9, 31.4, "苏州"),
            QuizStation("德清", 119.9, 30.5, "湖州"),
            QuizStation("未知", 120.0, 30.0),
        )
        val day = (0..31).map { date.plusDays(it.toLong()) }
            .first { quizEligible("city-station:杭州:千岛湖", it) }
        val question = quizCityQuestions(stations, day).single { it.id == "city-station:杭州:千岛湖" }
        assertEquals("千岛湖", question.options[question.correctIndex].label)
        assertTrue(question.options.none { it.label.contains("杭州") || it.label == "未知" })
        assertEquals(1, question.options.count { choice -> stations.single { it.name == choice.label }.city == "杭州" })
    }

    @Test fun eleventhDirectionIsIncludedWithoutExceedingTwentyDailyQuestions() {
        val questions = DailyQuizGenerator(sources).generate(date)
        assertEquals(20, questions.size)
        assertTrue(questions.any { it.category == 11 })
    }
}
