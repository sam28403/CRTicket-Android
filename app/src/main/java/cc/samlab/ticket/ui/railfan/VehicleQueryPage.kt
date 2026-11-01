package cc.samlab.ticket.ui.railfan

import cc.samlab.ticket.ui.trainlist.TrainListRow

internal data class VehicleQueryPage(
    val id: Int,
    val query: VehicleSearch?,
    val rows: List<VehicleHistory>,
    val trainRows: Map<String, TrainListRow>,
    val error: Boolean,
    val loading: Boolean,
)
