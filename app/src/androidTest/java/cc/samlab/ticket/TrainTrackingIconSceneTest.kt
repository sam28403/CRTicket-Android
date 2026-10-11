package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainTrackingIconSceneTest {
    @Test fun captureAllFourStatesOnAodAndVerifyChangingNumber() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        val output = File(context.getExternalFilesDir(null), "tracking-icon-scenes").apply { mkdirs() }
        val notices = TrainTrackingNotifications(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            fixture.open()
            TrainTrackingService.stop(context)
            val timeline = TrainTrackingStore.parse(fixture.countdownPayload())
            val moments = listOf(
                "waiting" to timeline.start - 300_000,
                "running" to timeline.start + 60_000,
                "station" to timeline.stops[1].arrival!!,
                "arrived" to timeline.end,
            )
            for ((phase, now) in moments) {
                notices.publish(notices.build(timeline, now))
                fixture.shell("input keyevent KEYCODE_SLEEP")
                Thread.sleep(3500)
                val entry = manager.activeNotifications.first { it.id == TrainTrackingNotifications.NOTIFICATION_ID }
                val expectedCode = if (phase == "waiting" || phase == "running") "G97" else "G100"
                assertEquals("$expectedCode · 调试起点→调试终点", entry.notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE).toString())
                println("Icon scene: $phase icon=${entry.notification.smallIcon.resId} " +
                    "title=$expectedCode flags=${entry.notification.flags}")
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(output, "$phase.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            fixture.restore()
        }
    }
}
