package cc.samlab.ticket.tracking

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver

class TrainTrackingService : Service() {
    companion object {
        const val PAYLOAD = "schedule"
        const val RESULT = "result"

        fun stop(context: Context) {
            TrainTrackingStore.clear(context)
            context.stopService(Intent(context, TrainTrackingService::class.java))
            TrainTrackingNotifications(context).cancel()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notifications: TrainTrackingNotifications
    private var timeline: TrainTimeline? = null
    private var session: TrainTrackingSession? = null
    private val tick = Runnable { update() }
    private val clockChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update()
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = TrainTrackingNotifications(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(clockChanges, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(clockChanges, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra<ResultReceiver>(RESULT)
        try {
            val requested = intent?.getStringExtra(PAYLOAD)
            val payload = requested ?: TrainTrackingStore.read(this)
            require(payload != null) { "没有正在追踪的列车" }
            val next = TrainTrackingStore.parse(payload)
            require(notifications.enabled()) { "请在系统设置中开启列车追踪通知" }
            val now = System.currentTimeMillis()
            val nextSession = if (requested != null) TrainTrackingSession.start(next.end, now)
                else TrainTrackingStore.session(this) ?: TrainTrackingSession.restoreLegacy(next.end)
            if (nextSession.expired(now)) {
                stop(this)
                return START_NOT_STICKY
            }
            val notification = notifications.build(next, now, session = nextSession)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    TrainTrackingNotifications.NOTIFICATION_ID,
                    notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(TrainTrackingNotifications.NOTIFICATION_ID, notification)
            }
            timeline = next
            session = nextSession
            TrainTrackingStore.save(this, payload, nextSession)
            update()
            receiver?.send(1, Bundle().apply {
                putBoolean("finished", next.status(System.currentTimeMillis()).phase == TrainPhase.ARRIVED)
            })
            return if (next.status(now).phase == TrainPhase.ARRIVED) START_NOT_STICKY else START_STICKY
        } catch (error: Exception) {
            receiver?.send(0, Bundle().apply {
                putString("message", error.message ?: "无法启动列车追踪")
            })
            TrainTrackingStore.clear(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun update() {
        handler.removeCallbacks(tick)
        val current = timeline ?: return
        try {
            if (!notifications.enabled()) {
                stop(this)
                return
            }
            val now = System.currentTimeMillis()
            val currentSession = session ?: return
            if (currentSession.expired(now)) {
                stop(this)
                return
            }
            val status = current.status(now)
            if (status.phase == TrainPhase.ARRIVED) {
                // Retain the terminal chip until the persisted deadline; no foreground timer remains.
                stopForeground(STOP_FOREGROUND_DETACH)
                notifications.publish(notifications.build(current, now,
                    quietUpdate = true, session = currentSession))
                stopSelf()
                return
            }
            notifications.publish(notifications.build(current, now,
                quietUpdate = true, session = currentSession))
            val minuteBoundary = 60_000 - now % 60_000
            val eventDelay = (status.nextEvent!! - now).coerceAtLeast(1)
            val chipDelay = if (Build.VERSION.SDK_INT >= 36 &&
                status.phase == TrainPhase.AT_STATION &&
                getSystemService(PowerManager::class.java).isInteractive) {
                val stationStop = current.stops.first { it.departure == status.nextEvent }
                TrainStationChip.nextFrameDelay(status.station, stationStop.arrival!!, now)
            } else {
                null
            }
            handler.postDelayed(tick, minOf(minuteBoundary, eventDelay, chipDelay ?: Long.MAX_VALUE))
        } catch (_: SecurityException) {
            stop(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(clockChanges)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
