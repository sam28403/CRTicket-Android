package cc.samlab.ticket.ui.quiz

import androidx.annotation.StringRes
import java.time.LocalDate

internal enum class QuizOptionKind { STATION, TRAIN, TEXT }

internal data class QuizText(@StringRes val resource: Int, val arguments: List<String> = emptyList())

internal data class QuizChoice(
    val label: String,
    val kind: QuizOptionKind,
    val from: String = "",
    val to: String = "",
)

internal data class QuizQuestion(
    val id: String,
    val category: Int,
    val title: QuizText,
    val options: List<QuizChoice>,
    val correctIndex: Int,
    val statement: QuizText,
)

internal data class QuizSession(
    val date: LocalDate,
    val sourceVersion: String,
    val questions: List<QuizQuestion>,
    val answers: List<Int> = List(questions.size) { -1 },
    val current: Int = 0,
    val completed: Boolean = false,
    val answerTimes: List<Long?> = List(questions.size) { null },
    val seenKeys: Set<String> = emptySet(),
) {
    val remaining: Int get() = answers.count { it < 0 }

    fun resetProgress() = copy(
        answers = List(questions.size) { -1 },
        current = 0,
        completed = false,
        answerTimes = List(questions.size) { null },
    )
}

internal data class QuizStation(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val city: String = "",
)
internal data class QuizStop(val station: String, val arrival: Int?, val departure: Int?, val km: Double?)
internal data class QuizSchedule(val start: LocalDate?, val end: LocalDate?, val period: Int, val mask: Long) {
    fun runs(date: LocalDate): Boolean {
        if (start == null || end == null || date < start || date > end) return false
        return period == 0 || mask shr ((date.toEpochDay() - start.toEpochDay()) % period).toInt() and 1L == 1L
    }
}
internal data class QuizTrain(val code: String, val stops: List<QuizStop>, val schedule: QuizSchedule) {
    val distance: Double? get() = stops.lastOrNull()?.km?.takeIf { it > 0 }
    val duration: Int? get() = stops.lastOrNull()?.arrival?.let { end ->
        stops.firstOrNull()?.departure?.let { start -> (end - start).takeIf { it > 0 } }
    }
    val speed: Double? get() = distance?.let { km -> duration?.let { km * 60 / it } }
    fun choice(showRoute: Boolean = true) = QuizChoice(
        code, QuizOptionKind.TRAIN,
        if (showRoute) stops.first().station else "",
        if (showRoute) stops.last().station else "",
    )
}
internal data class QuizLine(val name: String, val stops: List<Pair<String, Double?>>)
internal data class QuizRoute(val start: LocalDate, val end: LocalDate, val trains: List<String>)
internal data class QuizSources(
    val version: String,
    val stations: List<QuizStation>,
    val trains: List<QuizTrain>,
    val lines: List<QuizLine>,
    val routes: List<QuizRoute>,
)
