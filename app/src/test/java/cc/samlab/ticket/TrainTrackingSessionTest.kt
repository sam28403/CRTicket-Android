package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainTrackingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrainTrackingSessionTest {
    private val arrival = Instant.parse("2026-10-12T16:00:00Z").toEpochMilli()

    @Test fun trackingBeforeArrivalExpiresThirtyMinutesAfterTheScheduledArrival() {
        for (start in listOf(arrival - 3 * 86_400_000L, arrival - 1)) {
            val session = TrainTrackingSession.start(arrival, start)
            assertEquals(arrival + 30 * 60_000, session.expiresAt)
            assertFalse(session.expired(arrival + 30 * 60_000 - 1))
            assertTrue(session.expired(arrival + 30 * 60_000))
            assertEquals(30 * 60_000L, session.remaining(arrival))
            assertEquals(0L, session.remaining(arrival + 31 * 60_000))
        }
    }

    @Test fun startingAtOrAfterArrivalExpiresThreeMinutesAfterStarting() {
        for (start in listOf(arrival, arrival + 2 * 86_400_000L)) {
            val session = TrainTrackingSession.start(arrival, start)
            assertEquals(start + 3 * 60_000, session.expiresAt)
            assertFalse(session.expired(start + 3 * 60_000 - 1))
            assertTrue(session.expired(start + 3 * 60_000))
        }
    }

    @Test fun restoringExistingMetadataKeepsItsDeadlineAndNewTrackingHasANewIdentity() {
        val original = TrainTrackingSession.start(arrival, arrival - 60_000)
        val restored = TrainTrackingSession(original.id, original.expiresAt)
        assertEquals(original, restored)
        assertEquals(20 * 60_000L, restored.remaining(arrival + 10 * 60_000))
        val restarted = TrainTrackingSession.start(arrival, arrival + 10 * 60_000)
        assertNotEquals(original.id, restarted.id)
        assertEquals(arrival + 13 * 60_000, restarted.expiresAt)
    }

    @Test fun legacyTrackingUsesArrivalRatherThanExtendingTheDeadlineOnRestore() {
        val session = TrainTrackingSession.restoreLegacy(arrival)
        assertEquals(arrival + 30 * 60_000, session.expiresAt)
        assertTrue(session.expired(arrival + 31 * 60_000))
    }
}
