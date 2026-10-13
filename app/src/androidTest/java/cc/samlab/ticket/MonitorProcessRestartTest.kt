package cc.samlab.ticket

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorNotifications
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run prepare and verify in separate instrumentation invocations, with force-stop between them. */
@RunWith(AndroidJUnit4::class)
class MonitorProcessRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixture = MonitorLifecycleDeviceFixture(instrumentation)
    private val context = fixture.context
    private val backup = File(context.filesDir, "monitor-process-restart-fixture.json")

    @Test fun processRestartPhase() {
        when (InstrumentationRegistry.getArguments().getString("monitorRestartPhase")) {
            "prepare-active" -> prepare(active = true)
            "prepare-finished" -> prepare(active = false)
            "verify" -> verify()
            "cleanup" -> cleanup()
            else -> assumeTrue("Process-restart phases require a host-driven force-stop between invocations", false)
        }
    }

    private fun prepare(active: Boolean) {
        check(!backup.exists()) { "A previous fixture remains; run monitorRestartPhase=cleanup first" }
        val saved = JSONObject()
            .put("snapshot", fixture.snapshot())
            .put("pid", Process.myPid())
            .put("active", active)
        backup.writeText(saved.toString())
        try {
            fixture.open()
            fixture.startTrain(MonitorTestPayloads.train(arrived = !active))
            fixture.onMain { TicketMonitorService.start(context, MonitorTestPayloads.tickets()) }
            fixture.await("Ticket monitor did not start before process restart") {
                TicketMonitorStore.status(context).optInt("attempts") > 0 &&
                    fixture.notice(TicketMonitorNotifications.ID) != null
            }
            val session = requireNotNull(TrainTrackingStore.session(context))
            saved.put("session", session.id)
                .put("expiresAt", session.expiresAt)
                .put("payload", TrainTrackingStore.read(context))
                .put("ticketConfig", TicketMonitorStore.status(context).getJSONObject("config"))
            if (!active) {
                fixture.onMain { TicketMonitorService.stop(context) }
                fixture.await("Terminal tracking service did not stop") {
                    !TrainTrackingService.isRunning(session.id)
                }
            }
            backup.writeText(saved.toString())
            fixture.finish()
            println("Prepared process restart: active=$active pid=${Process.myPid()} session=${session.id}")
        } catch (error: Throwable) {
            cleanup()
            throw error
        }
    }

    private fun verify() {
        check(backup.exists()) { "No prepared process-restart fixture" }
        val saved = JSONObject(backup.readText())
        try {
            assertNotEquals("The target process must restart between phases", saved.getInt("pid"), Process.myPid())
            fixture.open()
            if (saved.getBoolean("active")) verifyActive(saved) else verifyFinished(saved)
            println("Verified process restart: oldPid=${saved.getInt("pid")} newPid=${Process.myPid()}")
        } finally {
            cleanup()
        }
    }

    private fun verifyActive(saved: JSONObject) {
        val id = saved.getString("session")
        fixture.await("Train service did not recover after process restart") {
            TrainTrackingService.isRunning(id) &&
                fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID) != null
        }
        assertEquals(saved.getString("payload"), TrainTrackingStore.read(context))
        assertEquals(id, requireNotNull(TrainTrackingStore.session(context)).id)
        assertEquals(saved.getLong("expiresAt"), requireNotNull(TrainTrackingStore.session(context)).expiresAt)
        assertTrue(fixture.trainState().getBoolean("active"))
        fixture.await("Ticket foreground service did not recover after process restart") {
            fixture.notice(TicketMonitorNotifications.ID) != null
        }
        val status = TicketMonitorStore.status(context)
        assertTrue(status.getBoolean("active"))
        assertEquals(saved.getJSONObject("ticketConfig").toString(), status.getJSONObject("config").toString())
        val attempts = status.optInt("attempts")
        fixture.await("Recovered ticket service is not polling", timeout = 80_000) {
            TicketMonitorStore.status(context).optInt("attempts") > attempts
        }
    }

    private fun verifyFinished(saved: JSONObject) {
        assertFalse(fixture.trainState().getBoolean("active"))
        assertFalse(TrainTrackingService.isRunning(saved.getString("session")))
        assertNull(TrainTrackingStore.read(context))
        assertNull(fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID))
        assertFalse(TicketMonitorStore.status(context).getBoolean("active"))
        assertNull(fixture.notice(TicketMonitorNotifications.ID))
    }

    private fun cleanup() {
        if (!backup.exists()) return
        fixture.restore(JSONObject(backup.readText()).getJSONObject("snapshot"))
        check(backup.delete()) { "Could not remove process-restart fixture backup" }
    }
}
