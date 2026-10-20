package cc.samlab.ticket.ui.trainlist

import androidx.annotation.StringRes
import cc.samlab.ticket.R

internal data class TrainListRow(
    val train: String,
    val from: String,
    val to: String,
    val duration: String,
    val stations: String,
    val mileage: String,
    val speed: String,
    val sourceIndex: Int,
    val distanceKm: Double?,
    val speedKmh: Double?,
    val stationCount: Double?,
) {
    val durationMinutes: Double? = parseTrainListDuration(duration)
}

internal fun parseTrainListDuration(duration: String): Double? {
    val parts = duration.split(":")
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours < 0 || minutes !in 0..59) return null
    return hours.toDouble() * 60 + minutes
}

internal enum class TrainListSort(@StringRes val label: Int) {
    TRAIN(R.string.train_list_sort_train),
    DURATION(R.string.train_list_sort_duration),
    MILEAGE(R.string.train_list_sort_mileage),
    SPEED(R.string.train_list_sort_speed),
    STATIONS(R.string.train_list_sort_stations),
}

internal fun sortTrainList(
    rows: List<TrainListRow>,
    sort: TrainListSort,
    descending: Boolean,
): List<TrainListRow> {
    // Snapshots already follow the railway source's natural train category/number order.
    if (sort == TrainListSort.TRAIN) return if (descending) rows.asReversed() else rows
    fun value(row: TrainListRow): Double? = when (sort) {
        TrainListSort.DURATION -> row.durationMinutes
        TrainListSort.MILEAGE -> row.distanceKm
        TrainListSort.SPEED -> row.speedKmh
        TrainListSort.STATIONS -> row.stationCount
        TrainListSort.TRAIN -> null
    }?.takeIf { it.isFinite() }
    return rows.sortedWith { left, right ->
        val a = value(left)
        val b = value(right)
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }
}
