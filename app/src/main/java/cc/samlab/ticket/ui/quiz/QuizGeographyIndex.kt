package cc.samlab.ticket.ui.quiz

import kotlin.math.abs
import kotlin.math.cos

internal object QuizGeographyIndex {
    private data class Index(val stations: List<QuizStation>, val neighbors: Map<String, List<QuizStation>>)
    @Volatile private var cached: Index? = null

    fun neighbors(stations: List<QuizStation>): Map<String, List<QuizStation>> {
        cached?.takeIf { it.stations === stations }?.let { return it.neighbors }
        return synchronized(this) {
            cached?.takeIf { it.stations === stations }?.neighbors ?: build(stations).also {
                cached = Index(stations, it)
            }
        }
    }

    private fun build(stations: List<QuizStation>): Map<String, List<QuizStation>> = stations.associate { anchor ->
        val longitudeLimit = 0.56 / cos(Math.toRadians(anchor.latitude)).coerceAtLeast(0.2)
        val nearby = mutableListOf<Pair<QuizStation, Double>>()
        for (other in stations) {
            if (other.name == anchor.name || abs(other.latitude - anchor.latitude) > 0.56 ||
                abs(other.longitude - anchor.longitude) > longitudeLimit) continue
            val distance = quizStationDistanceKm(anchor, other)
            if (distance in 1.0..60.0) nearby.add(other to distance)
        }
        anchor.name to nearby.sortedWith(
            compareBy<Pair<QuizStation, Double>> { it.second }.thenBy { it.first.name },
        ).take(12).map { it.first }
    }
}
