package cc.samlab.ticket.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopTicketMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TicketMonitorService.stop(context)
}
