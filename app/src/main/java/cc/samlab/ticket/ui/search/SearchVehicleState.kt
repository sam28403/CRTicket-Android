package cc.samlab.ticket.ui.search

import androidx.compose.runtime.mutableStateOf
import cc.samlab.ticket.ui.railfan.VehicleHistory

internal class SearchVehicleState {
    val latest = mutableStateOf<VehicleHistory?>(null)
    val loading = mutableStateOf(true)
    val error = mutableStateOf(false)
    var completed = false
}
