package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class TrainStationChipSceneTest {
    @Test fun serviceSlidesFiveCharacterStationAndKeepsFullNotificationBody() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        val manager = context.getSystemService(NotificationManager::class.java)
        val output = File(context.getExternalFilesDir(null), "tracking-station-scenes")
            .apply { mkdirs() }
        try {
            fixture.open()
            val payload = JSONObject(fixture.countdownPayload())
            val base = LocalDate.parse(payload.getString("date"))
            val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .withSecond(0).withNano(0)
            val departure = now.plusMinutes(5)
            payload.getJSONArray("stops").getJSONObject(1)
                .put("station", "乌鲁木齐南")
                .put("arrival", now.toLocalTime().toString())
                .put("arrivalDay", ChronoUnit.DAYS.between(base, now.toLocalDate()))
                .put("departure", departure.toLocalTime().toString())
                .put("departureDay", ChronoUnit.DAYS.between(base, departure.toLocalDate()))
            fixture.start(payload.toString())
            fixture.shell("cmd statusbar expand-notifications")
            val chips = mutableSetOf<String>()
            repeat(5) { frame ->
                Thread.sleep(1_500)
                val notice = manager.activeNotifications.first {
                    it.id == TrainTrackingNotifications.NOTIFICATION_ID
                }.notification
                chips.add(requireNotNull(notice.shortCriticalText))
                val body = notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                assertTrue(body.startsWith("当前到站：乌鲁木齐南，0h"))
                assertTrue(body.contains("后出发\n前方到站：调试终点，预计到达："))
                if (frame == 0 || frame == 1 || frame == 3) {
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(output, "frame-$frame.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                if (frame == 0) {
                    fixture.shell("cmd statusbar collapse")
                    fixture.shell("input keyevent KEYCODE_HOME")
                }
            }
            assertEquals(setOf("乌鲁木齐", "鲁木齐南"), chips)
        } finally {
            fixture.shell("cmd statusbar collapse")
            fixture.shell("am start -n cc.samlab.ticket/.MainActivity")
            fixture.restore()
        }
    }
}
