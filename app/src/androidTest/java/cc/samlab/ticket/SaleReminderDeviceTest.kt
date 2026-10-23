package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
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

@RunWith(AndroidJUnit4::class)
class SaleReminderDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun dailyCleanupClearsAppointmentsAndCancelsRemainingAlarms() {
        val store = SaleReminderStore(context)
        val saved = store.preferences.all.toMap()
        val row = SaleReminder("北京南", System.currentTimeMillis() + 180_000, "京局")
        try {
            assertFalse(store.add(row.copy(saleAt = row.saleAt + 86_400_000)))
            assertTrue(store.add(row))
            instrumentation.runOnMainSync {
                SaleReminderReceiver().onReceive(context,
                    Intent(context, SaleReminderReceiver::class.java).putExtra("cleanup", true))
            }
            assertTrue(store.read().isEmpty())
            assertFalse(store.preferences.contains("items"))
            assertTrue(store.preferences.getStringSet("alarms", emptySet()).orEmpty().isEmpty())
        } finally {
            val editor = store.preferences.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.commit()
            SaleReminderAlarms.reschedule(context)
        }
    }

    @Test fun subscribeOpenThirdLevelAndSwipeDelete() {
        val fixture = TabletPageFixture(compose)
        val store = SaleReminderStore(context)
        val original = store.read().map { it.id }.toSet()
        try {
            fixture.evaluate("window.crticketNavigate('/sale-time')")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("sale-query").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sale-query").performTextReplacement("北京南")
            compose.onAllNodes(hasText("北京南") and hasClickAction()).onLast().performClick()
            compose.onNodeWithTag("sale-search").performClick()
            compose.onNodeWithTag("sale-same-city").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("sale-subscribe:北京南").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sale-subscribe:北京南").performClick()
            compose.waitUntil(5_000) { store.read().any { it.station == "北京南" && it.id !in original } }
            val icon = compose.onNodeWithTag("sale-subscribe:北京南").fetchSemanticsNode().boundsInRoot
            val bureau = compose.onNodeWithText("京局").fetchSemanticsNode().boundsInRoot
            assertTrue("Reminder icon must be left of bureau", icon.right <= bureau.left)
            fixture.capture("sale-time-subscribe")
            val subscribed = store.read().first { it.station == "北京南" && it.id !in original }
            val peer = SaleReminder("北京西", subscribed.saleAt + 900_000, "京局")
            assertTrue(store.add(peer))
            compose.onNodeWithTag("sale-reminders-open").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("sale-reminder-list").fetchSemanticsNodes().isNotEmpty()
            }
            val reminder = compose.onNodeWithTag("sale-reminder:${subscribed.id}")
            reminder.assertIsDisplayed()
            compose.onNodeWithText(subscribed.instant.atZone(cc.samlab.ticket.ui.sale.saleZone)
                .toLocalDate().toString()).assertDoesNotExist()
            fixture.capture("sale-reminders-list")
            reminder.performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { store.read().none { it.id == subscribed.id } }
            compose.onNodeWithText("已删除 北京南").assertIsDisplayed()
            fixture.capture("sale-reminders-delete")
            compose.onNodeWithText("撤销").performClick()
            compose.waitUntil(5_000) { store.read().any { it.id == subscribed.id } }
            compose.onNodeWithTag("sale-reminder:${subscribed.id}").assertIsDisplayed()
                .performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { store.read().none { it.id == subscribed.id } }
            compose.onNodeWithTag("sale-reminder:${peer.id}").performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { store.read().none { it.id == peer.id } }
            compose.onNodeWithTag("sale-reminder-empty").assertIsDisplayed()
            fixture.capture("sale-reminders-empty")
            compose.onNodeWithText("撤销").performClick()
            compose.waitUntil(5_000) { store.read().any { it.id == peer.id } }
            compose.onNodeWithTag("sale-reminder:${peer.id}").assertIsDisplayed()
        } finally {
            store.read().filter { it.id !in original }.forEach { store.remove(it.id) }
            fixture.close()
        }
    }

    @Test fun exactAlarmMergesStationsAndDuplicateDeliveryDoesNotRepeatNotification() {
        val store = SaleReminderStore(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val saleAt = System.currentTimeMillis() + 125_000
        val first = SaleReminder("北京南", saleAt, "京局")
        val second = SaleReminder("北京西", saleAt, "京局")
        val tag = "sale-reminder-$saleAt"
        SaleReminderReceiver.createChannel(context)
        try {
            assertTrue(SaleReminderAlarms.allowed(context))
            assertTrue(store.add(first))
            assertTrue(store.add(second))
            assertEquals(1, store.preferences.getStringSet("alarms", emptySet())!!.count { it == "$saleAt" })
            compose.waitUntil(20_000) { manager.activeNotifications.any { it.tag == tag } }
            val notice = manager.activeNotifications.single { it.tag == tag }
            val body = notice.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue(body.contains("北京南"))
            assertTrue(body.contains("北京西"))
            assertTrue(store.wasDelivered(saleAt))
            val intent = Intent(context, SaleReminderReceiver::class.java).putExtra("saleAt", saleAt)
            instrumentation.runOnMainSync { SaleReminderReceiver().onReceive(context, intent) }
            SystemClock.sleep(200)
            assertEquals(notice.postTime, manager.activeNotifications.single { it.tag == tag }.postTime)
        } finally {
            store.remove(first.id)
            store.remove(second.id)
            manager.cancel(tag, 510)
            val delivered = store.preferences.getStringSet("delivered", emptySet()).orEmpty() - "$saleAt"
            store.preferences.edit().putStringSet("delivered", delivered).commit()
        }
    }
}
