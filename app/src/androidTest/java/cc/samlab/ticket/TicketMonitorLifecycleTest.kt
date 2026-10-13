package cc.samlab.ticket

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorNotifications
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TicketMonitorLifecycleTest {
    private val fixture = MonitorLifecycleDeviceFixture(InstrumentationRegistry.getInstrumentation())
    private val context = fixture.context

    @Test fun homeAndScreenOffContinueQueryingWithoutAnActivity() = fixture.withSnapshot {
        start()
        fixture.shell("input keyevent KEYCODE_HOME")
        val beforeHome = attempts()
        awaitNextAttempt(beforeHome, "Home background stopped ticket polling")
        fixture.finish()
        fixture.shell("input keyevent KEYCODE_SLEEP")
        val beforeSleep = attempts()
        awaitNextAttempt(beforeSleep, "Screen-off background stopped ticket polling")
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
        assertForegroundNotice()
        println("Ticket background attempts: $beforeHome -> $beforeSleep -> ${attempts()}")
    }

    @Test fun reopeningRestartsALostServiceWithTheSavedConfiguration() = fixture.withSnapshot {
        start()
        val before = attempts()
        val config = TicketMonitorStore.status(context).getJSONObject("config").toString()
        fixture.finish()
        fixture.onMain { context.stopService(Intent(context, TicketMonitorService::class.java)) }
        fixture.await("Ticket service did not stop") {
            fixture.notice(TicketMonitorNotifications.ID) == null
        }
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
        fixture.open()
        awaitNextAttempt(before, "Reopening did not resume ticket polling")
        assertEquals(config, TicketMonitorStore.status(context).getJSONObject("config").toString())
        assertForegroundNotice()
    }

    @Test fun explicitStopRemainsStoppedAfterReopening() = fixture.withSnapshot {
        start()
        fixture.onMain { TicketMonitorService.stop(context) }
        fixture.await("Explicitly stopped ticket notification did not disappear") {
            fixture.notice(TicketMonitorNotifications.ID) == null
        }
        fixture.finish()
        fixture.open()
        assertFalse(TicketMonitorStore.status(context).getBoolean("active"))
        assertNull(fixture.notice(TicketMonitorNotifications.ID))
        val stoppedAt = attempts()
        Thread.sleep(1_500)
        assertEquals(stoppedAt, attempts())
    }

    @Test fun reopeningAnExpiredTicketDateStopsTheExistingMonitor() = fixture.withSnapshot {
        start()
        val state = TicketMonitorStore.read(context)
        state.getJSONObject("config").put("date",
            LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString())
        TicketMonitorStore.save(context, state)
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
        assertForegroundNotice()
        fixture.open()
        fixture.await("Expired ticket monitor was not stopped on reopen") {
            !TicketMonitorStore.status(context).getBoolean("active") &&
                fixture.notice(TicketMonitorNotifications.ID) == null
        }
        val stoppedAt = attempts()
        Thread.sleep(1_500)
        assertEquals(stoppedAt, attempts())
    }

    private fun start() {
        fixture.onMain { TicketMonitorService.start(context, MonitorTestPayloads.tickets()) }
        fixture.await("Ticket monitor did not begin its first query") {
            attempts() > 0 && fixture.notice(TicketMonitorNotifications.ID) != null
        }
        assertForegroundNotice()
    }

    private fun attempts() = TicketMonitorStore.status(context).optInt("attempts")

    private fun awaitNextAttempt(before: Int, message: String) {
        // A real upstream query may consume its timeout before the 10-second retry delay.
        fixture.await(message, timeout = 80_000) { attempts() > before }
    }

    private fun assertForegroundNotice() {
        val notice = requireNotNull(fixture.notice(TicketMonitorNotifications.ID))
        assertTrue(notice.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertTrue(notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
