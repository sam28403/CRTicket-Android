package cc.samlab.ticket.speedometer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import cc.samlab.ticket.MainActivity
import cc.samlab.ticket.R

class SpeedometerService : Service(), LocationListener {
    private lateinit var locations: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var tickAt = 0L
    private var fixAt = 0L
    private var segment = 0
    private var checkpointAt = 0L
    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            SpeedometerStore.current?.let { record ->
                if (!SpeedometerStore.paused) {
                    SpeedometerStore.current = record.copy(elapsed = record.elapsed + now - tickAt)
                }
            }
            tickAt = now
            SpeedometerStore.signal = fixAt > 0 && now - fixAt <= 10_000 && !SpeedometerStore.paused
            if (!SpeedometerStore.signal) SpeedometerStore.speed = null
            if (now - checkpointAt > 10_000) {
                SpeedometerStore.checkpoint()
                checkpointAt = now
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        SpeedometerStore.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> {
                ticker.runTick()
                SpeedometerStore.finish()
                stopSelf()
                return START_NOT_STICKY
            }
            PAUSE -> {
                if (SpeedometerStore.current == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ticker.runTick()
                SpeedometerStore.paused = !SpeedometerStore.paused
                segment++
                fixAt = 0L
                SpeedometerStore.speed = null
                SpeedometerStore.checkpoint()
                return START_NOT_STICKY
            }
        }
        if (SpeedometerStore.current != null) return START_NOT_STICKY
        val train = intent?.getStringExtra("train") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        SpeedometerStore.current = SpeedRecording(
            java.util.UUID.randomUUID().toString(), train,
            intent.getStringExtra("from").orEmpty(), intent.getStringExtra("to").orEmpty(),
            System.currentTimeMillis(),
            vehicle = intent.getStringExtra("vehicle").orEmpty(),
        )
        SpeedometerStore.paused = false
        SpeedometerStore.error = false
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, getString(R.string.railfan_speedometer), NotificationManager.IMPORTANCE_LOW,
            ))
            val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_RECORDING, "/railfan/speedometer/live"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 11,
                Intent(this, SpeedometerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_home_speedometer)
                .setContentTitle(getString(R.string.speed_notification, train))
                .setContentText(getString(R.string.speed_notification_body))
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, getString(R.string.speed_stop), stop).build())
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(741, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(741, notification)
            tickAt = SystemClock.elapsedRealtime()
            handler.post(ticker)
            @Suppress("MissingPermission")
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            SpeedometerStore.checkpoint()
        } catch (_: Exception) {
            SpeedometerStore.error = true
            SpeedometerStore.current = null
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun Runnable.runTick() {
        handler.removeCallbacks(this)
        run()
    }

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (SpeedometerStore.paused || now - location.elapsedRealtimeNanos / 1_000_000 > 10_000) return
        if (!location.hasAccuracy() || location.accuracy > 50f) return
        val record = SpeedometerStore.current ?: return
        val point = SpeedPoint(location.latitude, location.longitude,
            location.elapsedRealtimeNanos / 1_000_000, segment)
        val previous = record.points.lastOrNull()
        if (previous != null && point.time <= previous.time) return
        val dt = previous?.let { (point.time - it.time) / 1000.0 } ?: 0.0
        val derived = if (previous != null && previous.segment == segment && dt > 0) {
            distanceMeters(previous, point) / dt * 3.6
        } else null
        val measured = if (location.hasSpeed() &&
            (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond <= 5f)) {
            location.speed * 3.6
        } else derived?.takeIf { dt <= 10 }
        // Reject impossible jumps; a genuine signal gap is connected on the next accepted fix.
        if (derived != null && dt <= 10 && derived > 500 ||
            measured != null && measured !in 0.0..500.0) return
        fixAt = now
        SpeedometerStore.signal = true
        SpeedometerStore.speed = measured
        val next = record.append(point)
        SpeedometerStore.current = if (measured != null && measured < 1.5 && dt <= 10) {
            next.copy(meters = record.meters)
        } else next
    }

    override fun onProviderDisabled(provider: String) {
        fixAt = 0
        SpeedometerStore.signal = false
        SpeedometerStore.speed = null
    }

    override fun onProviderEnabled(provider: String) = Unit

    @Deprecated("Required for LocationListener compatibility on Android 8–10")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        locations.removeUpdates(this)
        SpeedometerStore.finish()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "speedometer"
        const val STOP = "speedometer.stop"
        const val PAUSE = "speedometer.pause"
        const val OPEN_RECORDING = "openSpeedRecording"

        fun command(context: Context, action: String) {
            context.startService(Intent(context, SpeedometerService::class.java).setAction(action))
        }
    }
}
