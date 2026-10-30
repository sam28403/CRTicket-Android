package cc.samlab.ticket

import cc.samlab.ticket.ui.sale.parseSaleAnnouncement
import cc.samlab.ticket.sale.SaleReminder
import cc.samlab.ticket.sale.todayStationSale
import cc.samlab.ticket.sale.isTodayReminder
import cc.samlab.ticket.sale.reminderGroups
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class SaleAnnouncementTest {
    private fun fixture(): String = checkNotNull(javaClass.getResource("/sale-announcement.html")).readText()

    @Test fun parsesAllSlotsIncludingNestedMarkupWithoutFooterOrCommentStations() {
        val rows = parseSaleAnnouncement(fixture() + "<!-- 8:00 起售车站 假车站。 -->")
        assertEquals(3209, rows.size)
        assertEquals(LocalTime.of(8, 30), rows["成都龙泉驿"])
        assertEquals(LocalTime.of(15, 30), rows["寿安"])
        assertEquals(41, rows.values.toSet().size)
        assertEquals(LocalTime.of(17, 0), rows["南通东"])
        assertEquals(LocalTime.of(9, 15), rows["深圳北"])
        assertEquals(LocalTime.of(9, 45), rows["青岛"])
        assertEquals(LocalTime.of(12, 45), rows["北京南"])
        assertFalse("假车站" in rows)
    }

    @Test fun rejectsPartialResponsesInsteadOfReplacingCompleteCache() {
        assertThrows(IllegalArgumentException::class.java) { parseSaleAnnouncement(fixture().take(4000)) }
    }

    @Test fun remindersDeduplicateAndMergeByExactSaleInstantInTimeOrder() {
        val first = SaleReminder("北京南", 2000, "京局")
        val peer = SaleReminder("北京西", 2000, "京局")
        val later = SaleReminder("广州南", 3000, "广铁")
        val groups = reminderGroups(listOf(later, peer, first, first))
        assertEquals(listOf(2000L, 3000L), groups.keys.toList())
        assertEquals(2, groups.getValue(2000).size)
        assertEquals(1, groups.getValue(3000).size)
    }

    @Test fun subscriptionsOnlyAcceptPendingSalesTodayAndClearAtEighteen() {
        val now = Instant.parse("2026-10-23T02:00:00Z")
        assertNull(todayStationSale(LocalTime.of(10, 0), now))
        assertEquals(Instant.parse("2026-10-23T02:15:00Z").toEpochMilli(),
            todayStationSale(LocalTime.of(10, 15), now))
        assertNull(todayStationSale(LocalTime.of(8, 0), Instant.parse("2026-10-23T10:00:00Z")))
        val today = SaleReminder("北京南", Instant.parse("2026-10-23T04:45:00Z").toEpochMilli(), "京局")
        assertTrue(isTodayReminder(today, now))
        assertFalse(isTodayReminder(today.copy(saleAt = today.saleAt + 86_400_000), now))
        assertFalse(isTodayReminder(today, Instant.parse("2026-10-23T10:00:00Z")))
    }
}
