package cc.samlab.ticket.monitor

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

class TicketMonitorService : Service() {
    companion object {
        @Volatile private var running = false

        fun start(context: Context, payload: String) {
            val config = TicketMonitorConfig.parse(payload)
            TicketMonitorStore.save(context, JSONObject()
                .put("active", true).put("config", config.json())
                .put("revision", UUID.randomUUID().toString())
                .put("trains", JSONArray()).put("events", JSONArray()).put("attempts", 0))
            context.startForegroundService(Intent(context, TicketMonitorService::class.java))
        }

        fun update(context: Context, payload: String) {
            val config = TicketMonitorConfig.parse(payload)
            val state = TicketMonitorStore.read(context)
            if (!state.optBoolean("active")) return
            TicketMonitorStore.save(context, state.put("config", config.json())
                .put("revision", UUID.randomUUID().toString())
                .put("previous", JSONObject()).put("events", JSONArray()))
            context.startForegroundService(Intent(context, TicketMonitorService::class.java))
        }

        fun refresh(context: Context) {
            if (!TicketMonitorStore.read(context).optBoolean("active")) return
            context.startForegroundService(Intent(context, TicketMonitorService::class.java))
        }

        fun restore(context: Context) {
            val state = TicketMonitorStore.read(context)
            if (!state.optBoolean("active")) return
            if (runCatching {
                    TicketMonitorConfig.parse(state.getJSONObject("config").toString())
                }.isFailure) {
                stop(context)
                return
            }
            if (running) return
            runCatching {
                context.startForegroundService(Intent(context, TicketMonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            TicketMonitorStore.stop(context)
            context.stopService(Intent(context, TicketMonitorService::class.java))
            context.getSystemService(NotificationManager::class.java)
                .cancel(TicketMonitorNotifications.ID)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val client = TicketMonitorClient()
    private lateinit var notifications: TicketMonitorNotifications
    private lateinit var wakeLock: PowerManager.WakeLock
    private var generation = 0
    private val tick = Runnable { query() }

    override fun onCreate() {
        super.onCreate()
        notifications = TicketMonitorNotifications(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TicketMonitor")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = TicketMonitorStore.read(this)
        if (!state.optBoolean("active")) {
            stopSelf()
            return START_NOT_STICKY
        }
        val config = runCatching {
            TicketMonitorConfig.parse(state.getJSONObject("config").toString())
        }.getOrElse {
            stop(this)
            return START_NOT_STICKY
        }
        try {
            val notification = notifications.ongoing(config, state.optString("error"))
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(TicketMonitorNotifications.ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(TicketMonitorNotifications.ID, notification)
            running = true
            generation++
            client.cancel()
            handler.removeCallbacks(tick)
            query()
            return START_STICKY
        } catch (error: Exception) {
            TicketMonitorStore.save(this, state.put("loading", false)
                .put("error", error.message ?: "暂时无法启动后台监控，请重开应用重试"))
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun query() {
        val state = TicketMonitorStore.read(this)
        if (!state.optBoolean("active")) return
        val config = runCatching {
            TicketMonitorConfig.parse(state.getJSONObject("config").toString())
        }.getOrElse {
            stop(this)
            return
        }
        val current = generation
        val revision = state.optString("revision")
        // Keep the user-requested polling timer running while the screen is off.
        // The bounded lease expires if the worker ever stalls and is released on stop.
        wakeLock.acquire(config.interval * 1000 + 55_000)
        TicketMonitorStore.save(this, state.put("loading", true).put("nextAt", "")
            .put("attempts", state.optInt("attempts") + 1)
            .put("attemptedAt", Instant.now().toString()))
        worker.execute {
            val result = runCatching { client.query(config) }
            handler.post {
                if (current != generation) return@post
                val latest = TicketMonitorStore.read(this)
                if (!latest.optBoolean("active") || latest.optString("revision") != revision) return@post
                result.onSuccess { rows ->
                    latest.put("trains", rows).put("queriedAt", Instant.now().toString()).put("error", "")
                    val details = TicketMonitorMatches.collect(latest, rows, config)
                    notifications.available(details)
                }.onFailure { error ->
                    latest.put("error", error.message ?: "查询失败，稍后自动重试")
                }
                latest.put("loading", false)
                    .put("nextAt", Instant.now().plusSeconds(config.interval).toString())
                TicketMonitorStore.save(this, latest)
                notifications.update(config, latest.optString("error"))
                handler.postDelayed(tick, config.interval * 1000)
            }
        }
    }

    override fun onDestroy() {
        running = false
        generation++
        handler.removeCallbacks(tick)
        client.cancel()
        worker.shutdownNow()
        if (wakeLock.isHeld) wakeLock.release()
        // Lifecycle destruction preserves active intent; only explicit stop clears it.
        val state = TicketMonitorStore.read(this)
        TicketMonitorStore.save(this, state.put("loading", false))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
