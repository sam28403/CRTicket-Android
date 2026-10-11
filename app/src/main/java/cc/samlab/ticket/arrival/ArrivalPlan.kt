package cc.samlab.ticket.arrival

import kotlin.math.*

data class ArrivalStation(val name: String, val latitude: Double, val longitude: Double)
data class ArrivalPlan(val stations: List<ArrivalStation>, val refreshRadius: Float)

object ArrivalPolicy {
    const val RADIUS = 300f
    const val COOLDOWN_MS = 30 * 60 * 1000L
    const val MAX_STATIONS = 99

    fun distance(lat: Double, lon: Double, station: ArrivalStation): Double {
        val deltaLat = Math.toRadians(station.latitude - lat)
        val deltaLon = Math.toRadians(station.longitude - lon)
        val a = sin(deltaLat / 2).pow(2) + cos(Math.toRadians(lat)) *
            cos(Math.toRadians(station.latitude)) * sin(deltaLon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun plan(stations: List<ArrivalStation>, lat: Double, lon: Double): ArrivalPlan {
        val sorted = stations.map { it to distance(lat, lon, it) }.sortedBy { it.second }
        // 第 100 个站仍在监控区域外；提前更新围栏，给系统回调延迟留余量。
        val nextDistance = sorted.getOrNull(MAX_STATIONS)?.second ?: 100_000.0
        val radius = ((nextDistance - RADIUS) / 3).coerceIn(1000.0, 30_000.0)
        return ArrivalPlan(sorted.take(MAX_STATIONS).map { it.first }, radius.toFloat())
    }

    fun canNotify(last: Long, now: Long): Boolean = last == 0L || now - last >= COOLDOWN_MS
}
