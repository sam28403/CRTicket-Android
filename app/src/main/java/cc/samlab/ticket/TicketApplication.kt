package cc.samlab.ticket

import android.app.Application
import androidx.core.content.ContextCompat
import cc.samlab.ticket.i18n.AppStrings

class TicketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val process = getSystemService(android.app.ActivityManager::class.java)
            .runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        if (process?.endsWith(":diagnostics") != true) {
            cc.samlab.ticket.diagnostics.FailureMonitor.install(this)
        }
        AppStrings.resolve = { id, arguments ->
            val localized = ContextCompat.getContextForLanguage(this)
            if (arguments.isEmpty()) localized.getString(id) else localized.getString(id, *arguments)
        }
    }
}
