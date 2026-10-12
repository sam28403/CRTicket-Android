package cc.samlab.ticket.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopTrainTrackingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getStringExtra(TrainTrackingNotifications.SESSION)
        if (session != TrainTrackingStore.session(context)?.id) return
        TrainTrackingService.stop(context)
    }
}
