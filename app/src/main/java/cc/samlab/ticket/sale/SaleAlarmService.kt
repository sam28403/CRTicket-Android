package cc.samlab.ticket.sale

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

class SaleAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: SaleAlarmPlayer
    private var saleAt = 0L
    private val expire = Runnable { stopSelf() }

    override fun onCreate() {
        super.onCreate()
        player = SaleAlarmPlayer(this) { stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val next = intent?.getLongExtra("saleAt", 0) ?: 0
        if (next <= System.currentTimeMillis() || next > System.currentTimeMillis() + 120_000) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (saleAt == next) return START_NOT_STICKY
        saleAt = next
        current = this
        val notification = SaleAlarmNotification.build(this, next, intent?.getStringExtra("body").orEmpty())
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(510, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(510, notification)
        SaleReminderStore(this).delivered(next)
        player.stop()
        player.start()
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 60_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: SaleAlarmService? = null
        internal val ringing: Boolean get() = current?.player?.playing == true

        internal fun start(context: Context, saleAt: Long, body: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, SaleAlarmService::class.java)
                    .putExtra("saleAt", saleAt).putExtra("body", body))
        }

        internal fun dismiss(context: Context, saleAt: Long) {
            if (current?.saleAt == saleAt) context.stopService(Intent(context, SaleAlarmService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel("sale-reminder-$saleAt", 510)
        }

        internal fun active(context: Context, saleAt: Long): Boolean =
            context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.notification.extras.getLong("saleAt") == saleAt || it.tag == "sale-reminder-$saleAt"
            }
    }
}
