package cc.samlab.ticket.arrival

import android.content.Context
import android.location.Location
import android.os.SystemClock

/** 围栏和低功耗定位共用的判定，覆盖最近 99 个围栏之外的站点。 */
object ArrivalVisits {
    fun check(context: Context, location: Location?) {
        if (location == null || !location.hasAccuracy() || location.accuracy > 300f) return
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (age !in 0..300_000) return
        val store = ArrivalStore(context)
        if (!store.enabled || !ArrivalAccess.ready(context)) return
        val station = ArrivalCatalog.stations(context)
            .map { it to ArrivalPolicy.distance(location.latitude, location.longitude, it) }
            .filter { it.second <= ArrivalPolicy.RADIUS }
            .minByOrNull { it.second }?.first ?: return
        val now = System.currentTimeMillis()
        if (ArrivalPolicy.canNotify(store.lastNotification(station.name), now) &&
            ArrivalNotifications(context).publish(station.name)) {
            store.notified(station.name, now)
        }
    }
}
