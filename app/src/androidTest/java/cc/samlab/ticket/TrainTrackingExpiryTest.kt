package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingSession
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TrainTrackingExpiryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifications = TrainTrackingNotifications(context)

    @Test fun terminalStartUsesThreeMinutesAndSystemTimeoutClearsTheTrackingState() =
        withFixture { fixture ->
            val payload = historicalPayload(fixture)
            val timeline = TrainTrackingStore.parse(payload)
            val before = System.currentTimeMillis()
            fixture.start(payload)
            val after = System.currentTimeMillis()
            val session = requireNotNull(TrainTrackingStore.session(context))
            assertTrue(session.expiresAt in (before + 180_000)..(after + 180_000))
            await { notice()?.flags?.and(Notification.FLAG_FOREGROUND_SERVICE) == 0 }
            assertTrue(requireNotNull(notice()).timeoutAfter in 170_000..180_000)
            assertTrue(fixture.state().getBoolean("active"))
            assertEquals(session.expiresAt, fixture.state().getLong("expiresAt"))

            // Shorten only this test session to exercise the real system timeout and delete receiver.
            val deadline = System.currentTimeMillis() + 2_000
            val accelerated = session.copy(expiresAt = deadline)
            TrainTrackingStore.save(context, payload, accelerated)
            notifications.publish(notifications.build(timeline, System.currentTimeMillis(),
                quietUpdate = true, session = accelerated))
            await { notice() == null && TrainTrackingStore.read(context) == null }
            val clearedAt = System.currentTimeMillis()
            assertTrue("System timeout cleared ${deadline - clearedAt} ms before the deadline",
                clearedAt >= deadline)
            assertNull(TrainTrackingStore.session(context))
            assertFalse(fixture.state().getBoolean("active"))
        }

    @Test fun existingJourneyUsesArrivalDeadlineAndLateOldCallbacksCannotStopNewTracking() =
        withFixture { fixture ->
            val payload = fixture.countdownPayload()
            val timeline = TrainTrackingStore.parse(payload)
            fixture.start(payload)
            val original = requireNotNull(TrainTrackingStore.session(context))
            assertEquals(timeline.end + 1_800_000, original.expiresAt)
            val terminal = notifications.build(timeline, timeline.end, session = original)
            val later = notifications.build(timeline, timeline.end + 600_000, session = original)
            assertEquals(1_800_000L, terminal.timeoutAfter)
            assertEquals(1_200_000L, later.timeoutAfter)

            fixture.start(payload)
            val replacement = requireNotNull(TrainTrackingStore.session(context))
            assertNotEquals(original.id, replacement.id)
            terminal.deleteIntent.send()
            Thread.sleep(300)
            assertEquals(replacement, TrainTrackingStore.session(context))
            assertTrue(fixture.state().getBoolean("active"))
            TrainTrackingService.stop(context)
            assertNull(TrainTrackingStore.read(context))
            assertNull(TrainTrackingStore.session(context))
            await { notice() == null }
        }

    @Test fun stateReadCleansExpiredMetadataAndDoesNotExtendItsDeadline() =
        withFixture { fixture ->
            TrainTrackingService.stop(context)
            val payload = historicalPayload(fixture)
            val timeline = TrainTrackingStore.parse(payload)
            val session = TrainTrackingSession("expired-fixture", System.currentTimeMillis() - 1)
            TrainTrackingStore.save(context, payload, session)
            notifications.publish(notifications.build(timeline, System.currentTimeMillis()))
            await { notice() != null }
            assertFalse(fixture.state().getBoolean("active"))
            assertNull(TrainTrackingStore.read(context))
            assertNull(TrainTrackingStore.session(context))
            await { notice() == null }
        }

    @Test fun legacyTerminalTrackingAlreadyPastThirtyMinutesIsRemovedOnStateRead() =
        withFixture { fixture ->
            TrainTrackingService.stop(context)
            val payload = historicalPayload(fixture)
            val timeline = TrainTrackingStore.parse(payload)
            TrainTrackingStore.save(context, payload)
            notifications.publish(notifications.build(timeline, System.currentTimeMillis()))
            await { notice() != null }
            assertFalse(fixture.state().getBoolean("active"))
            assertNull(TrainTrackingStore.read(context))
            await { notice() == null }
        }

    private fun historicalPayload(fixture: TrainTrackingDeviceFixture): String {
        val data = JSONObject(fixture.countdownPayload())
        data.put("date", LocalDate.parse(data.getString("date")).minusDays(1).toString())
        return data.toString()
    }

    private fun notice(): Notification? = manager.activeNotifications.firstOrNull {
        it.id == TrainTrackingNotifications.NOTIFICATION_ID
    }?.notification

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Tracking expiry condition timed out")
    }

    private fun withFixture(test: (TrainTrackingDeviceFixture) -> Unit) {
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        try {
            fixture.open()
            test(fixture)
        } finally {
            fixture.restore()
        }
    }
}
