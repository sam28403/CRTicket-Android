package cc.samlab.ticket

import android.app.Notification
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainPhase
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class TrainPartialDelayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixture = MonitorLifecycleDeviceFixture(instrumentation)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private lateinit var welcome: WelcomeTestState

    @Before fun prepareStartup() {
        welcome = WelcomeTestState(fixture.context)
    }

    @After fun restoreStartup() = welcome.close()

    @Test fun arrivalOnlyDelayKeepsFollowingLegAndForegroundNotification() = fixture.withSnapshot {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).withSecond(0).withNano(0)
        // Shift the reported A/B pattern to now, without changing the device clock.
        val scheduledA = now.minusMinutes(70)
        val start = scheduledA.minusMinutes(30)
        val date = start.toLocalDate()
        fun row(station: String, arrival: ZonedDateTime?, departure: ZonedDateTime?) = JSONObject()
            .put("station", station)
            .put("arrival", arrival?.format(clock) ?: JSONObject.NULL)
            .put("departure", departure?.format(clock) ?: JSONObject.NULL)
            .put("arrivalDay", arrival?.let { ChronoUnit.DAYS.between(date, it.toLocalDate()) })
            .put("departureDay", departure?.let { ChronoUnit.DAYS.between(date, it.toLocalDate()) })

        val scheduled = JSONArray()
            .put(row("测试起点", null, start))
            .put(row("上海虹桥", scheduledA, scheduledA.plusMinutes(6)))
            .put(row("测试B站", scheduledA.plusMinutes(20), scheduledA.plusMinutes(24)))
            .put(row("测试终点", scheduledA.plusMinutes(40), null))
        val corrected = JSONArray(scheduled.toString())
        corrected.getJSONObject(1)
            .put("arrival", scheduledA.plusMinutes(60).format(clock))
            .put("arrivalOffset", 60)
        val payload = JSONObject().put("train", "G7535").put("date", date.toString())
            .put("scheduledStops", scheduled).put("stops", corrected)
            .put("delayEnabled", true).toString()
        val timeline = TrainTrackingStore.parse(payload)
        val expectedArrivalB = scheduledA.plusMinutes(80).toInstant().toEpochMilli()
        val expectedDepartureB = scheduledA.plusMinutes(84).toInstant().toEpochMilli()
        assertEquals(expectedArrivalB, timeline.stops[2].arrival)
        assertEquals(expectedDepartureB, timeline.stops[2].departure)
        assertEquals(TrainPhase.RUNNING, timeline.status(now.toInstant().toEpochMilli()).phase)
        assertEquals(TrainPhase.AT_STATION, timeline.status(expectedArrivalB).phase)
        assertEquals(TrainPhase.RUNNING, timeline.status(expectedDepartureB).phase)

        fixture.startTrain(payload)
        val session = requireNotNull(TrainTrackingStore.session(fixture.context))
        fixture.await("Corrected tracking notification did not appear") {
            fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID)
                ?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.contains("测试B站") == true
        }
        assertTrue(TrainTrackingService.isRunning(session.id))
        val notice = requireNotNull(fixture.notice(TrainTrackingNotifications.NOTIFICATION_ID))
        assertTrue(notice.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertEquals(payload, TrainTrackingStore.read(fixture.context))
        try {
            fixture.shell("cmd statusbar expand-notifications")
            Thread.sleep(2_000)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val folder = File(fixture.context.getExternalFilesDir(null), "tablet-native-pages")
                .apply { mkdirs() }
            File(folder, "partial-delay-tracking.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            fixture.shell("cmd statusbar collapse")
        }
    }
}
