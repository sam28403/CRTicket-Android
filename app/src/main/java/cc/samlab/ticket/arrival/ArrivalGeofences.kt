package cc.samlab.ticket.arrival

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.CancellationTokenSource

class ArrivalGeofences(private val context: Context) {
    companion object {
        const val EVENTS = "cc.samlab.ticket.ARRIVAL_GEOFENCE"
        const val UPDATES = "cc.samlab.ticket.ARRIVAL_LOCATION"
        const val REFRESH = "arrival:refresh"
        const val PREFIX = "station:"
    }
    private val client = LocationServices.getGeofencingClient(context)
    private val location = LocationServices.getFusedLocationProviderClient(context)
    private var currentToken: CancellationTokenSource? = null

    private fun pending(action: String, code: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, code,
            Intent(context, StationArrivalReceiver::class.java).setAction(action), flags)
    }

    @SuppressLint("MissingPermission")
    fun replace(plan: ArrivalPlan, center: Location, stillEnabled: () -> Boolean): Task<Void> {
        val fences = plan.stations.map { station ->
            Geofence.Builder().setRequestId(PREFIX + station.name)
                .setCircularRegion(station.latitude, station.longitude, ArrivalPolicy.RADIUS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(60_000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE).build()
        } + Geofence.Builder().setRequestId(REFRESH)
            .setCircularRegion(center.latitude, center.longitude, plan.refreshRadius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(60_000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE).build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or
                GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofences(fences).build()
        val intent = pending(EVENTS, 501)
        return client.removeGeofences(intent).continueWithTask {
            if (stillEnabled()) client.addGeofences(request, intent)
            else Tasks.forResult<Void>(null)
        }
    }

    @SuppressLint("MissingPermission")
    fun passiveUpdates(): Task<Void> {
        // 只接收系统或其他应用已经取得的位置，不主动启动周期定位。
        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 300_000L)
            .setMinUpdateIntervalMillis(120_000L)
            .setMinUpdateDistanceMeters(1000f)
            .setMaxUpdateDelayMillis(300_000L).build()
        return location.requestLocationUpdates(request, pending(UPDATES, 502))
    }

    @SuppressLint("MissingPermission")
    fun current(): Task<Location> {
        currentToken?.cancel()
        val token = CancellationTokenSource().also { currentToken = it }
        return location.getCurrentLocation(CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(120_000L).setDurationMillis(15_000L).build(), token.token)
    }

    fun remove(): Task<Void> {
        currentToken?.cancel()
        currentToken = null
        val fences = client.removeGeofences(pending(EVENTS, 501))
        val updates = location.removeLocationUpdates(pending(UPDATES, 502))
        return Tasks.whenAll(fences, updates)
    }
}
