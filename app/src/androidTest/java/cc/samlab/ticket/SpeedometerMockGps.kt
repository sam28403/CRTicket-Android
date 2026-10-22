package cc.samlab.ticket

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import java.io.Closeable
import kotlin.math.cos

/** Inject through the system GPS provider used by the production foreground service. */
@Suppress("DEPRECATION")
internal class SpeedometerMockGps(context: Context) : Closeable {
    private val manager = context.getSystemService(LocationManager::class.java)

    init {
        manager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, true, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
        )
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    fun inject(route: Int, seconds: Double): Long {
        val latitude = if (route == 1) 31.194 else if (route == 2) 23.145 else 39.864
        val longitude = if (route == 1) 121.323 else if (route == 2) 113.258 else 116.378
        val east = when (route) {
            1 -> seconds.coerceAtMost(20.0) * 20 + (seconds - 40).coerceAtLeast(0.0) * 20
            2 -> seconds * 20 + if (seconds >= 19) 600 else 0
            else -> seconds * 20
        }
        val north = if (route == 1) (seconds - 20).coerceIn(0.0, 20.0) * 20 else 0.0
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude + north / 111_195
            this.longitude = longitude + east / (111_195 * cos(Math.toRadians(latitude)))
            accuracy = 5f
            speed = 20f
            speedAccuracyMetersPerSecond = 0.5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        return location.elapsedRealtimeNanos / 1_000_000
    }

    override fun close() {
        manager.removeTestProvider(LocationManager.GPS_PROVIDER)
    }
}
