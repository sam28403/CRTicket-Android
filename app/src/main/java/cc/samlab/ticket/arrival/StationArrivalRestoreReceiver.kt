package cc.samlab.ticket.arrival

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StationArrivalRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.location.PROVIDERS_CHANGED")) {
            val finish = arrivalCompletion()
            runCatching { StationArrivalMonitor.get(context).restore(finish) }.onFailure { finish() }
        }
    }
}
