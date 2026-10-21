package cc.samlab.ticket.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.RailfanPreferences

internal val railfanDestinations = listOf(
    HomeDestination(
        R.string.railfan_vehicle, R.string.vehicle_query_description,
        R.drawable.ic_home_vehicle, "/railfan/vehicle",
    ),
    HomeDestination(
        R.string.railfan_routing, R.string.routing_query_description,
        R.drawable.ic_home_routing, "/railfan/routing",
    ),
    HomeDestination(
        R.string.railfan_visits, R.string.visits_description,
        R.drawable.ic_home_visits, "/railfan/visits",
    ),
    HomeDestination(
        R.string.railfan_speedometer, R.string.railfan_placeholder,
        R.drawable.ic_home_speedometer, "/railfan/speedometer",
    ),
)

@Composable
internal fun HomeRailfanTools(onNavigate: (String) -> Unit, selectedRoute: String) {
    val enabled by RailfanPreferences.rememberEnabled()
    if (!enabled) return
    HomeDestinations(railfanDestinations, ticketDestinations.size, onNavigate, selectedRoute)
}
