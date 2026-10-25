package cc.samlab.ticket.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.RailfanPreferences

internal val railfanDestinations = listOf(
    HomeDestination(
        R.string.fare_title, R.string.fare_description,
        R.drawable.ic_home_fare, "/price",
    ),
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
        R.string.railfan_speedometer, R.string.speed_description,
        R.drawable.ic_home_speedometer, "/railfan/speedometer",
    ),
)

@Composable
internal fun HomeRailfanTools(onNavigate: (String) -> Unit, selectedRoute: String) {
    val enabled by RailfanPreferences.rememberEnabled()
    if (!enabled) return
    val context = LocalContext.current
    val destinations = remember(context) {
        val hasGps = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        railfanDestinations.filter { hasGps || it.route != "/railfan/speedometer" }
    }
    HomeDestinations(destinations, ticketDestinations.size, onNavigate, selectedRoute)
}
