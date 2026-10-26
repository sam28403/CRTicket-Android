package cc.samlab.ticket

import cc.samlab.ticket.ui.quiz.*
import java.time.LocalDate

internal fun dailyQuizTestData(): QuizSources {
    val stations = List(220) { index ->
        QuizStation("Station$index", 110.0 + index * 0.02, 30.0 + (index * 37 % 220) * 0.01)
    }.sortedBy { it.name }
    val schedule = QuizSchedule(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1), 0, 0)
    val trains = (0 until 600).flatMap { index ->
        val from = "Station${index * index % 199}"
        val via = "Station${(index * index + 1) % 199}"
        val via2 = "Station${(index * index + 2) % 199}"
        val to = "Station${(index * index + 3) % 199}"
        val departure = 300 + index % 600
        val duration = 60 + index * 11 % 1800
        val km = (100 + index * 13 % 4000).toDouble()
        listOf(
            QuizTrain("G${index * 2 + 1}", listOf(
                QuizStop(from, null, departure, 0.0),
                QuizStop(via, departure + 20, departure + 25, km / 4),
                QuizStop(via2, departure + 40, departure + 45, km / 2),
                QuizStop(to, departure + duration, null, km),
            ), schedule),
            QuizTrain("G${index * 2 + 2}", listOf(
                QuizStop(to, null, departure, 0.0),
                QuizStop(via2, departure + 20, departure + 25, km / 4),
                QuizStop(via, departure + 40, departure + 45, km / 2),
                QuizStop(from, departure + duration, null, km),
            ), schedule),
        )
    }.sortedBy { it.code }
    val lines = List(12) { index -> QuizLine("Line$index",
        List(40) { stop -> "Station${(index * 13 + stop) % 220}" to stop * 20.0 }) }
    val routes = List(600) { index -> QuizRoute(schedule.start!!, schedule.end!!,
        listOf("G${index * 2 + 1}", "G${index * 2 + 2}")) }
    return QuizSources("fixture", stations, trains, lines, routes)
}
