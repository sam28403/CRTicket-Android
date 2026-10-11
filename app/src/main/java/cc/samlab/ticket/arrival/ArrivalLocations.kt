package cc.samlab.ticket.arrival

import android.location.Location
import android.os.SystemClock

object ArrivalLocations {
    fun usable(location: Location, maxAccuracy: Float): Boolean {
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) /
            1_000_000
        return age in 0..300_000 && location.hasAccuracy() &&
            !(location.accuracy > maxAccuracy)
    }
}
