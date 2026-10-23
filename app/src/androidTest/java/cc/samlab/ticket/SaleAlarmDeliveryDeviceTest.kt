package cc.samlab.ticket

import android.app.KeyguardManager
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.sale.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SaleAlarmDeliveryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun sleepingPhoneAutomaticallyShowsAlarmAndPlaysAudio() {
        val manager = context.getSystemService(NotificationManager::class.java)
        println("ALARM_ACCESS dnd=${manager.isNotificationPolicyAccessGranted} " +
            "fullscreen=${Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()}")
        assertTrue(SaleReminderPreferences.alarmAccess(context))
        val original = SaleReminderPreferences.mode(context)
        val store = SaleReminderStore(context)
        val saved = store.preferences.all.toMap()
        val fixture = TabletPageFixture(compose)
        val saleAt = System.currentTimeMillis() + 130_000
        try {
            SaleReminderPreferences.setMode(context, SaleReminderMode.ALARM)
            assertTrue(store.add(SaleReminder("北京南", saleAt, "京局")))
            assertTrue(store.add(SaleReminder("北京西", saleAt, "京局")))
            fixture.shell("input keyevent 3")
            fixture.shell("input keyevent 223")
            SystemClock.sleep(1500)
            assertTrue(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!SaleAlarmService.active(context, saleAt) &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
            SystemClock.sleep(1500)
            val folder = File(context.getExternalFilesDir(null), "tablet-native-pages")
            val audio = fixture.shell("dumpsys audio")
            File(folder, "sale-alarm-audio.txt").writeText(audio)
            File(folder, "sale-alarm-notification.txt").writeText(fixture.shell("dumpsys notification --noredact"))
            File(folder, "sale-alarm-activity.txt").writeText(fixture.shell("dumpsys activity activities"))
            fixture.capture("sale-alarm-automatic")
            assertTrue("The alarm must have a running audio player", SaleAlarmService.ringing)
            val playing = Regex("AudioPlaybackConfiguration[^\\n]+u/pid:${android.os.Process.myUid()}/" +
                "[^\\n]+state:started[^\\n]+USAGE_ALARM[^\\n]+mutedState:none")
            assertTrue("System audio must report an unmuted ALARM stream", playing.containsMatchIn(audio))
            compose.onNodeWithText(context.getString(R.string.sale_alarm_dismiss)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sale_alarm_dismiss)).performClick()
            compose.waitUntil(5000) {
                !SaleAlarmService.active(context, saleAt) && !SaleAlarmService.ringing
            }
        } finally {
            SaleAlarmService.dismiss(context, saleAt)
            SaleReminderPreferences.setMode(context, original)
            val editor = store.preferences.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
            SaleReminderAlarms.reschedule(context)
            fixture.shell("input keyevent 224")
            fixture.close()
        }
    }
}
