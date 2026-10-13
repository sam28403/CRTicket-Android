package cc.samlab.ticket.tracking

import android.content.Context
import android.content.Intent

data class SavedTrainTracking(
    val payload: String,
    val timeline: TrainTimeline,
    val session: TrainTrackingSession,
)

class TrainTrackingRecovery(
    private val context: Context,
    private val notifications: TrainTrackingNotifications = TrainTrackingNotifications(context),
) {
    fun read(now: Long = System.currentTimeMillis()): SavedTrainTracking? {
        val payload = TrainTrackingStore.read(context) ?: return null
        val timeline = runCatching { TrainTrackingStore.parse(payload) }.getOrElse {
            TrainTrackingService.stop(context)
            return null
        }
        val storedSession = TrainTrackingStore.session(context)
        val session = storedSession ?: TrainTrackingSession.restoreLegacy(timeline.end)
        if (session.expired(now) ||
            (now >= timeline.end && !notifications.isActive())) {
            TrainTrackingService.stop(context)
            return null
        }
        if (storedSession == null) {
            TrainTrackingStore.save(context, payload, session)
            if (now >= timeline.end && notifications.enabled()) {
                // Migrate a legacy terminal notice once so it receives the saved deadline.
                runCatching {
                    notifications.publish(notifications.build(timeline, now,
                        quietUpdate = true, session = session))
                }
            }
        }
        return SavedTrainTracking(payload, timeline, session)
    }

    fun restore(): Boolean {
        val now = System.currentTimeMillis()
        val saved = read(now) ?: return false
        if (!notifications.enabled()) return false
        if (!saved.session.canResume(saved.timeline.end, now)) {
            // Leave the existing system timeout alone when reading a terminal notice.
            return notifications.isActive()
        }
        if (TrainTrackingService.isRunning(saved.session.id)) return true
        // A surviving notification is not proof that its service survived process termination.
        return runCatching {
            context.startForegroundService(Intent(context, TrainTrackingService::class.java))
        }.isSuccess
    }
}
