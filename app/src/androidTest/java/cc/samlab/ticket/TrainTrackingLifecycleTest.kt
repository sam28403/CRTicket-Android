package cc.samlab.ticket

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainTrackingLifecycleTest {
    private val fixture = MonitorLifecycleDeviceFixture(InstrumentationRegistry.getInstrumentation())
    private val context = fixture.context

    @Test fun homeAndActivityRecreationRetainTheSameJourney() = fixture.withSnapshot {
        fixture.startTrain(MonitorTestPayloads.train())
        val original = requireNotNull(TrainTrackingStore.session(context))
        fixture.shell("input keyevent KEYCODE_HOME")
        Thread.sleep(1_500)
        assertTrue(TrainTrackingService.isRunning(original.id))
        assertForegroundNotice()
        fixture.finish()
        fixture.open()
        assertEquals(original, TrainTrackingStore.session(context))
        assertTrue(fixture.trainState().getBoolean("active"))
        assertForegroundNotice()
    }

    @Test fun reopeningRestoresALostServiceWithoutResettingTheDeadline() = fixture.withSnapshot {
        val payload = MonitorTestPayloads.train()
        fixture.startTrain(payload)
        val original = requireNotNull(TrainTrackingStore.session(context))
        fixture.finish()
        fixture.onMain {
            context.stopService(Intent(context, TrainTrackingService::class.java))
            fixture.cancelTrainNotice()
        }
        fixture.await("Old train service did not stop") { !TrainTrackingService.isRunning(original.id) }
        assertEquals(payload, TrainTrackingStore.read(context))
        fixture.open()
        fixture.await("Reopening did not resume the saved journey") {
            TrainTrackingService.isRunning(original.id) &&
                fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID) != null
        }
        assertEquals(original, TrainTrackingStore.session(context))
        assertTrue(fixture.trainState().getBoolean("active"))
        assertForegroundNotice()
    }

    @Test fun arrivedJourneyWithoutASurvivingNotificationDoesNotRestart() = fixture.withSnapshot {
        fixture.startTrain(MonitorTestPayloads.train(arrived = true))
        val session = requireNotNull(TrainTrackingStore.session(context))
        fixture.await("Terminal service did not finish") { !TrainTrackingService.isRunning(session.id) }
        fixture.finish()
        fixture.cancelTrainNotice()
        fixture.open()
        assertFalse(fixture.trainState().getBoolean("active"))
        assertFalse(TrainTrackingService.isRunning(session.id))
        assertNull(TrainTrackingStore.read(context))
        assertNull(fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID))
    }

    @Test fun explicitStopIsNotUndoneByReopening() = fixture.withSnapshot {
        fixture.startTrain(MonitorTestPayloads.train())
        fixture.onMain { TrainTrackingService.stop(context) }
        fixture.finish()
        fixture.open()
        assertFalse(fixture.trainState().getBoolean("active"))
        assertNull(TrainTrackingStore.read(context))
        assertNull(TrainTrackingStore.session(context))
        fixture.await("Stopped train notification was not cleared") {
            fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID) == null
        }
    }

    private fun assertForegroundNotice() {
        val notice = requireNotNull(fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID))
        assertTrue(notice.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertEquals("G97 · 恢复测试起点→恢复测试终点",
            notice.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }
}
