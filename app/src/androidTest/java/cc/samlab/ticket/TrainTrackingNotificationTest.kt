package cc.samlab.ticket

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingIcons
import cc.samlab.ticket.tracking.TrainPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainTrackingNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = TrainTrackingNotifications(context)
    private val timeline = TrainTimeline("G1", "2026-10-10", listOf(
        TrainStopSchedule("北京南", null, "09:00"),
        TrainStopSchedule("济南西", "10:00", "10:05"),
        TrainStopSchedule("上海虹桥", "11:05", null),
    ))

    @Test fun trackingRestoresTheOriginalChannelAndDefaultImportance() {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(TrainTrackingNotifications.CHANNEL_ID)
        assertEquals("train-tracking", channel.id)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test fun countdownUpdatesRetainRouteAndEmphasisWithoutFullScreenAlerts() {
        for (now in listOf(timeline.start, timeline.start + 60_000)) {
            val notice = notifications.build(timeline, now)
            assertTrue(notice.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertNull(notice.fullScreenIntent)
            assertNull(notice.contentView)
            assertNull(notice.bigContentView)
            assertEquals("G1 · 北京南→上海虹桥", notice.extras
                .getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(notice.extras.getCharSequence(Notification.EXTRA_TEXT)
                .toString().startsWith("前方到站：济南西\n"))
            assertNull(notice.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            val title = notice.extras.getCharSequence(Notification.EXTRA_TITLE) as Spanned
            val text = notice.extras.getCharSequence(Notification.EXTRA_TEXT) as Spanned
            assertTrue(title.getSpans(0, title.length, StyleSpan::class.java)
                .any { it.style == Typeface.BOLD })
            assertTrue(text.getSpans(0, text.length, StyleSpan::class.java)
                .any { it.style == Typeface.BOLD })
        }
    }

    @Test fun terminalNoticeRetainsAnOngoingPromotableChipUntilStopped() {
        val notice = notifications.build(timeline, timeline.end)
        assertTrue(notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notice.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(notice.actions.any { it.title.toString() == "停止追踪" })
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals("已达终点", notice.shortCriticalText)
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                assertTrue(notice.isRequestPromotedOngoing)
                assertTrue(notice.hasPromotableCharacteristics())
            }
        }
    }

    @Test fun stationNoticeKeepsRouteAndPromotionWithStationNameChip() {
        val notice = notifications.build(timeline, timeline.stops[1].arrival!!)
        assertEquals("G1 · 北京南→上海虹桥", notice.extras
            .getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "当前到站：济南西，0h5m后出发\n前方到站：上海虹桥，预计到达：11:05",
            notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(Notification.BigTextStyle::class.java.name,
            notice.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
            notice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertNull(notice.contentView)
        assertNull(notice.bigContentView)
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals("济南西", notice.shortCriticalText)
            assertEquals(timeline.stops[1].departure!!, notice.`when`)
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                assertTrue(notice.hasPromotableCharacteristics())
            }
        }
    }

    @Test fun trainsDaysBeforeDepartureAlsoRequestAStatusBarChip() {
        val notice = notifications.build(timeline, timeline.start - 3 * 86400000L)
        if (Build.VERSION.SDK_INT >= 36) {
            assertEquals("3d0h", notice.shortCriticalText)
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                assertTrue(notice.isRequestPromotedOngoing)
                assertTrue(notice.hasPromotableCharacteristics())
            }
        }
    }

    @Test fun allFourStatesUseDifferentSmallIconsOnSystemSurfaces() {
        val moments = mapOf(
            TrainPhase.WAITING to timeline.start - 60_000,
            TrainPhase.RUNNING to timeline.start,
            TrainPhase.AT_STATION to timeline.stops[1].arrival!!,
            TrainPhase.ARRIVED to timeline.end,
        )
        val icons = moments.map { (phase, now) ->
            val notice = notifications.build(timeline, now)
            assertEquals(TrainTrackingIcons.forPhase(phase), notice.smallIcon.resId)
            assertEquals(TrainTrackingIcons.forPhase(phase), notice.getLargeIcon().resId)
            notice.smallIcon.resId
        }
        assertEquals(4, icons.toSet().size)
    }

    @Test fun updatesSuppressVisualAlertsWithoutLoweringChannelOrLosingPromotion() {
        val notice = notifications.build(timeline, timeline.start, quietUpdate = true)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, notice.groupAlertBehavior)
        assertTrue(notice.group != null)
        assertFalse(notice.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(TrainTrackingNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        if (android.os.Build.VERSION.SDK_INT >= 36 &&
            android.os.Build.VERSION.SDK_INT_FULL >= android.os.Build.VERSION_CODES_FULL.BAKLAVA_1) {
            assertTrue(notice.hasPromotableCharacteristics())
        }
    }
}
