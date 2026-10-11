package cc.samlab.ticket.arrival

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult

class StationArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ArrivalStore(context)
        if (!store.enabled) return
        val monitor = StationArrivalMonitor.get(context)
        if (!ArrivalAccess.ready(context)) {
            monitor.restore()
            return
        }
        if (intent.action == ArrivalGeofences.UPDATES) {
            val location = LocationResult.extractResult(intent)?.lastLocation ?: return
            val finish = arrivalCompletion()
            runCatching { monitor.update(location, finished = finish) }.onFailure { finish() }
            return
        }
        if (intent.action != ArrivalGeofences.EVENTS) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            monitor.unavailable()
            return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT &&
            ArrivalGeofences.REFRESH in ids) {
            val finish = arrivalCompletion()
            runCatching { monitor.refresh(event.triggeringLocation, finish) }.onFailure { finish() }
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            ArrivalVisits.check(context, event.triggeringLocation)
        }
    }
}
