package cc.samlab.ticket

import cc.samlab.ticket.ui.sale.saleCountdown
import cc.samlab.ticket.ui.sale.saleRemaining
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import cc.samlab.ticket.ui.sale.SaleTime
import cc.samlab.ticket.ui.sale.rankSaleSlot
import cc.samlab.ticket.ui.sale.nextSaleSlot
import cc.samlab.ticket.ui.sale.saleDayComplete

class SaleTimeTest {
    @Test fun completedStatusStartsAtEighteenAndResetsAtBeijingMidnight() {
        org.junit.Assert.assertFalse(saleDayComplete(Instant.parse("2026-10-23T09:59:59.999Z")))
        org.junit.Assert.assertTrue(saleDayComplete(Instant.parse("2026-10-23T10:00:00Z")))
        org.junit.Assert.assertTrue(saleDayComplete(Instant.parse("2026-10-23T15:59:59.999Z")))
        org.junit.Assert.assertFalse(saleDayComplete(Instant.parse("2026-10-23T16:00:00Z")))
        org.junit.Assert.assertFalse(saleDayComplete(Instant.parse("2026-10-24T00:00:00Z")))
    }

    private val sale = LocalTime.of(10, 0)

    @Test fun usesBeijingDateAndKeepsLastFractionalSecondPending() {
        assertEquals(3600L, saleRemaining(sale, Instant.parse("2026-10-23T01:00:00Z")))
        assertEquals(1L, saleRemaining(sale, Instant.parse("2026-10-23T01:59:59.900Z")))
        assertEquals(0L, saleRemaining(sale, Instant.parse("2026-10-23T02:00:00Z")))
        assertEquals(0L, saleRemaining(sale, Instant.parse("2026-10-23T15:59:59Z")))
        assertEquals(36000L, saleRemaining(sale, Instant.parse("2026-10-23T16:00:00Z")))
    }

    @Test fun formatsHoursMinutesAndSeconds() {
        assertEquals("01:01:01", saleCountdown(3661))
        assertEquals("00:00:01", saleCountdown(1))
        assertEquals("00:00:00", saleCountdown(0))
    }

    @Test fun recommendationsOnlyUseNextSlotAndStopAtTen() {
        val traffic = (0..15).reversed().map { "Station$it" to (100 - it) }
        val schedule = (0..15).associate { "Station$it" to LocalTime.of(10, 15) } +
            mapOf("Station0" to sale)
        val result = rankSaleSlot(traffic, schedule - "Station1", Instant.parse("2026-10-23T02:00:00Z"))
        assertEquals((2..11).map { "Station$it" }, result.map { it.name })
    }

    @Test fun nextSlotUsesQuarterHoursAndRollsOverAfterLastSlot() {
        val samples = mapOf(
            "2026-10-23T01:40:00Z" to "2026-10-23T01:45:00Z",
            "2026-10-23T01:44:59.999Z" to "2026-10-23T01:45:00Z",
            "2026-10-23T01:45:00Z" to "2026-10-23T02:00:00Z",
            "2026-10-22T23:59:00Z" to "2026-10-23T00:00:00Z",
            "2026-10-23T09:59:00Z" to "2026-10-23T10:00:00Z",
            "2026-10-23T10:00:00Z" to "2026-10-24T00:00:00Z",
        )
        samples.forEach { (now, expected) ->
            assertEquals(Instant.parse(expected), nextSaleSlot(Instant.parse(now)).toInstant())
        }
        val now = Instant.parse("2026-10-23T10:00:00Z")
        val slot = nextSaleSlot(now)
        assertEquals(14 * 3600L, saleRemaining(slot.toLocalTime(), now, slot.toLocalDate()))
    }

    @Test fun sparseSlotReturnsAllMatchesWithoutFillingFromLaterSlots() {
        val times = mapOf(
            "BusyLater" to LocalTime.of(10, 0),
            "Small" to LocalTime.of(9, 45),
            "Busy" to LocalTime.of(9, 45),
            "Opened" to LocalTime.of(9, 30),
        )
        val result = rankSaleSlot(
            listOf("BusyLater" to 1000, "Small" to 5, "Busy" to 100, "Opened" to 200),
            times, Instant.parse("2026-10-23T01:40:00Z"),
        )
        assertEquals(listOf("Busy", "Small"), result.map { it.name })
    }
}
