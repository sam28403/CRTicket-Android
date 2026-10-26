package cc.samlab.ticket.ui.quiz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleQueryRepository
import cc.samlab.ticket.ui.railfan.VehicleSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

internal fun quizVehicleQuestions(
    records: Map<String, List<VehicleHistory>>,
    date: LocalDate,
): List<QuizQuestion> {
    val allVehicles = records.values.flatten().map { it.displayVehicle }.distinct().sorted()
    return records.toSortedMap().mapNotNull { (train, rows) ->
        val id = "vehicle-today:$train"
        if (!quizEligible(id, date)) return@mapNotNull null
        // All observations today must agree, including coupled sets. Never substitute yesterday's unit.
        val today = rows.filter { it.day == date.toString() && it.train.equals(train, ignoreCase = true) }
        if (today.map { it.vehicle }.distinct().size != 1) return@mapNotNull null
        val row = today.minByOrNull { it.date } ?: return@mapNotNull null
        quizQuestion(
            id, 9, QuizText(R.string.quiz_q_vehicle, listOf(train)),
            QuizChoice(row.displayVehicle, QuizOptionKind.TEXT),
            quizPicks(allVehicles.filter { it != row.displayVehicle }, id, 3)
                .map { QuizChoice(it, QuizOptionKind.TEXT) },
            QuizText(R.string.quiz_f_vehicle, listOf(train, row.displayVehicle, row.date)),
        )
    }
}

internal suspend fun loadQuizVehicles(context: Context, sources: QuizSources, date: LocalDate): List<QuizQuestion> {
    val network = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = network.getNetworkCapabilities(network.activeNetwork)
    if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) return emptyList()
    val candidates = sources.trains.filter { it.schedule.runs(date) }.map { it.code }.filter {
        Regex("^[GDC]\\d+$").matches(it) && quizEligible("vehicle-today:$it", date)
    }.distinct().sortedWith(compareBy<String> { quizHash("$date:vehicle:$it") }.thenBy { it }).take(6)
    val records = ConcurrentHashMap<String, List<VehicleHistory>>()
    withTimeoutOrNull(8000) {
        coroutineScope {
            candidates.map { train -> async {
                val rows = try {
                    VehicleQueryRepository.search(VehicleSearch(train, true))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { emptyList() }
                records[train] = rows
            } }.awaitAll()
        }
    }
    return quizVehicleQuestions(records, date)
}
