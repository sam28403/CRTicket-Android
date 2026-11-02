package cc.samlab.ticket.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

internal object FailureMonitor {
    private val reporting = AtomicBoolean(false)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                report(app, "Uncaught exception", thread, error)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        val started = AtomicInteger(0)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started.incrementAndGet() }
            override fun onActivityStopped(activity: Activity) { started.decrementAndGet() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (true) {
                if (started.get() > 0 && !Debug.isDebuggerConnected()) {
                    val acknowledged = AtomicBoolean(false)
                    val since = SystemClock.uptimeMillis()
                    main.post { acknowledged.set(true) }
                    while (!acknowledged.get() && SystemClock.uptimeMillis() - since < 8_000) {
                        Thread.sleep(250)
                    }
                    if (!acknowledged.get() && started.get() > 0 && !Debug.isDebuggerConnected()) {
                        report(app, "Main thread unresponsive (8 seconds)", Looper.getMainLooper().thread, null)
                    }
                }
                Thread.sleep(1_000)
            }
        }, "CRTicket responsiveness monitor").apply { isDaemon = true; start() }
    }

    private fun report(app: Application, kind: String, thread: Thread, error: Throwable?) {
        if (!reporting.compareAndSet(false, true)) return
        ErrorReports.save(app, kind, thread, error)
        app.startActivity(ErrorReportActivity.intent(app).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }
}
