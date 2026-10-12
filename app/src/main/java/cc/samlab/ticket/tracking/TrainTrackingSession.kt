package cc.samlab.ticket.tracking

import java.util.UUID

data class TrainTrackingSession(val id: String, val expiresAt: Long) {
    companion object {
        private const val ARRIVAL_RETENTION = 30 * 60_000L
        private const val TERMINAL_RETENTION = 3 * 60_000L

        fun start(arrival: Long, now: Long): TrainTrackingSession {
            val expiresAt = if (now < arrival) arrival + ARRIVAL_RETENTION
                else now + TERMINAL_RETENTION
            return TrainTrackingSession(UUID.randomUUID().toString(), expiresAt)
        }

        fun restoreLegacy(arrival: Long): TrainTrackingSession =
            TrainTrackingSession(UUID.randomUUID().toString(), arrival + ARRIVAL_RETENTION)
    }

    fun expired(now: Long): Boolean = now >= expiresAt

    fun remaining(now: Long): Long = (expiresAt - now).coerceAtLeast(0)
}
