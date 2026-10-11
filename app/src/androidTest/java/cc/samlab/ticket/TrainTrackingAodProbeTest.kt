package cc.samlab.ticket

import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainTrackingAodProbeTest {
    @Test fun observeSixteenToFifteenMinuteUpdateOnAod() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val label = args.getString("probeLabel", "baseline")!!
        val seconds = args.getString("probeSeconds", "85")!!.toLong()
        val output = File(context.getExternalFilesDir(null), "aod-probe-$label").apply { mkdirs() }
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        val manager = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val samples = StringBuilder()
        try {
            fixture.open()
            fixture.start(fixture.countdownPayload())
            Thread.sleep(1000)
            File(output, "doze-before.txt").writeText(fixture.shell(
                "dumpsys activity service com.android.systemui/.SystemUIService DozeLog"))
            fixture.shell("input keyevent KEYCODE_SLEEP")
            val started = SystemClock.elapsedRealtime()
            val deadline = started + seconds * 1000
            var previousChip = ""
            var frame = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                val notice = manager.activeNotifications.firstOrNull {
                    it.id == TrainTrackingNotifications.NOTIFICATION_ID
                }
                assertTrue("Tracking notification disappeared", notice != null)
                val chip = if (android.os.Build.VERSION.SDK_INT >= 36) {
                    notice!!.notification.shortCriticalText ?: ""
                } else "legacy"
                samples.append("${System.currentTimeMillis()},${power.isInteractive},$chip," +
                    "${notice!!.postTime},${notice.notification.flags}\n")
                if (chip != previousChip) {
                    println("AOD probe $label: chip=$chip interactive=${power.isInteractive} " +
                        "flags=${notice.notification.flags}")
                    previousChip = chip
                }
                val second = System.currentTimeMillis() / 1000 % 60
                if (SystemClock.elapsedRealtime() - started < 8000 || second >= 56 || second <= 8) {
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(output, "frame-${frame++}-$chip.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                Thread.sleep(500)
            }
            File(output, "samples.csv").writeText(samples.toString())
            File(output, "doze-after.txt").writeText(fixture.shell(
                "dumpsys activity service com.android.systemui/.SystemUIService DozeLog"))
            println("AOD probe output: ${output.absolutePath}")
        } finally {
            fixture.restore()
        }
    }
}
