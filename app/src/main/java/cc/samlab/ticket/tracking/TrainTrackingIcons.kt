package cc.samlab.ticket.tracking

import cc.samlab.ticket.R

object TrainTrackingIcons {
    fun forPhase(phase: TrainPhase): Int = when (phase) {
        TrainPhase.WAITING -> R.drawable.ic_tracking_waiting
        TrainPhase.RUNNING -> R.drawable.ic_tracking_running
        TrainPhase.AT_STATION -> R.drawable.ic_tracking_station
        TrainPhase.ARRIVED -> R.drawable.ic_tracking_arrived
    }
}
