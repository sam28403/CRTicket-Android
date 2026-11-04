package cc.samlab.ticket.ui.quiz

import java.time.LocalDate

internal data class QuizLiveData(
    val date: LocalDate,
    val sources: QuizSources,
    val stationCounts: Map<String, Int>,
)

internal fun QuizLiveData.verifiedSources(catalog: QuizSources, today: LocalDate): QuizSources {
    require(date == today && sources.trains.isNotEmpty() && stationCounts.isNotEmpty())
    // Coordinates/cities are reference catalogs; operational records never come from the asset.
    // Line membership/mileage questions stay disabled until they have a fresh online source.
    return sources.copy(stations = catalog.stations, lines = emptyList())
}
