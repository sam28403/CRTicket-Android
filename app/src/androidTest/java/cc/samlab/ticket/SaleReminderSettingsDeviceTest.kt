package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.sale.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleReminderSettingsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun leavingDndPermissionWithoutGrantKeepsNotificationMode() {
        org.junit.Assume.assumeFalse(context.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted)
        val original = SaleReminderPreferences.mode(context)
        SaleReminderPreferences.setMode(context, SaleReminderMode.NOTIFICATION)
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/settings/sale-reminders")
            compose.onNodeWithTag("sale-mode-ALARM").performClick()
            compose.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            Thread.sleep(800)
            assertEquals(SaleReminderMode.NOTIFICATION, SaleReminderPreferences.mode(context))
            fixture.shell("input keyevent 4")
            compose.onNodeWithTag("sale-mode-NOTIFICATION").assertIsSelected()
        } finally {
            SaleReminderPreferences.setMode(context, original)
            fixture.close()
        }
    }

    @Test fun settingsWarningModesAndAppointmentNavigation() {
        val original = SaleReminderPreferences.mode(context)
        SaleReminderPreferences.setMode(context, SaleReminderMode.NOTIFICATION)
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/settings")
            compose.onNodeWithText(context.getString(R.string.settings_sale_reminders))
                .performScrollTo().performClick()
            compose.onNodeWithTag("sale-mode-NOTIFICATION").assertIsSelected()
            fixture.capture("sale-reminder-settings")
            compose.onNodeWithTag("sale-mode-ALARM").performClick()
            compose.onNodeWithText(context.getString(R.string.sale_alarm_warning)).assertIsDisplayed()
            fixture.capture("sale-reminder-alarm-warning")
            compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
            assertEquals(SaleReminderMode.NOTIFICATION, SaleReminderPreferences.mode(context))
            compose.onNodeWithTag("sale-mode-ALARM").performClick()
            compose.onNodeWithText(context.getString(R.string.action_confirm)).performClick()
            compose.waitUntil(5000) { SaleReminderPreferences.mode(context) == SaleReminderMode.ALARM }
            compose.onNodeWithTag("sale-mode-ALARM").assertIsSelected()
            fixture.capture("sale-reminder-alarm-enabled")
            compose.onNodeWithText(context.getString(R.string.sale_reminders)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.sale_reminders)).assertIsDisplayed()
            fixture.shell("input keyevent 4")
            compose.onNodeWithTag("sale-mode-ALARM").assertIsSelected()
            compose.onNodeWithTag("sale-mode-NOTIFICATION").performClick()
            assertEquals(SaleReminderMode.NOTIFICATION, SaleReminderPreferences.mode(context))
        } finally {
            SaleReminderPreferences.setMode(context, original)
            fixture.close()
        }
    }

    @Test fun mergedAlarmUsesDndChannelAndDismissStopsIt() {
        val original = SaleReminderPreferences.mode(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(SaleReminderPreferences.alarmAccess(context))
        val store = SaleReminderStore(context)
        val saved = store.preferences.all.toMap()
        val saleAt = System.currentTimeMillis() + 125_000
        val fixture = TabletPageFixture(compose)
        try {
            SaleReminderPreferences.setMode(context, SaleReminderMode.ALARM)
            assertTrue(store.add(SaleReminder("北京南", saleAt, "京局")))
            assertTrue(store.add(SaleReminder("北京西", saleAt, "京局")))
            compose.waitUntil(15_000) {
                SaleAlarmService.active(context, saleAt)
            }
            compose.waitUntil(5000) { SaleAlarmService.ringing }
            val record = manager.activeNotifications.single {
                it.notification.extras.getLong("saleAt") == saleAt
            }
            val alarm = record.notification
            assertEquals(Notification.CATEGORY_ALARM, alarm.category)
            assertEquals(SaleAlarmNotification.CHANNEL, alarm.channelId)
            assertTrue(manager.getNotificationChannel(alarm.channelId).canBypassDnd())
            assertNull(manager.getNotificationChannel(alarm.channelId).sound)
            assertNotNull(alarm.fullScreenIntent)
            assertTrue(alarm.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            assertEquals(60_000L, alarm.timeoutAfter)
            val body = alarm.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue(body.contains("北京南") && body.contains("北京西"))
            instrumentation.runOnMainSync {
                SaleReminderReceiver().onReceive(context,
                    Intent(context, SaleReminderReceiver::class.java).putExtra("saleAt", saleAt))
            }
            assertEquals(record.postTime, manager.activeNotifications.single {
                it.notification.extras.getLong("saleAt") == saleAt
            }.postTime)
            instrumentation.runOnMainSync { alarm.actions.single().actionIntent.send() }
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
            fixture.close()
        }
    }
}
