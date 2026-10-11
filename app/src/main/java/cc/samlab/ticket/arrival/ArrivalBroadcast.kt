package cc.samlab.ticket.arrival

import android.content.BroadcastReceiver
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

// 在后台围栏更新期间保持接收器存活；超时兜底释放，不长期占用进程。
fun BroadcastReceiver.arrivalCompletion(): () -> Unit {
    val pending = goAsync()
    val finished = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    lateinit var timeout: Runnable
    val complete = {
        if (finished.compareAndSet(false, true)) {
            handler.removeCallbacks(timeout)
            pending.finish()
        }
        Unit
    }
    timeout = Runnable { complete() }
    handler.postDelayed(timeout, 25_000)
    return complete
}
