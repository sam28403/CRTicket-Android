package cc.samlab.ticket

import cc.samlab.ticket.ui.history.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySortingTest {
    private fun record(id: String, date: String, price: Double) = HistoryRecord(
        id, date, "08:00", "G1", "北京南", "上海虹桥", "二等座", "01车01A号", id, price, false,
    )

    @Test fun datesSupportChineseAndIsoFormatsAcrossMonthsAndYears() {
        val rows = listOf(
            record("a", "2026年2月8日", 100.0),
            record("b", "2026-10-16", 20.0),
            record("c", "2025年12月31日", 1000.0),
        )
        assertEquals(listOf("b", "a", "c"), sortHistory(rows, HistorySort.NEWEST).map { it.id })
        assertEquals(listOf("c", "a", "b"), sortHistory(rows, HistorySort.OLDEST).map { it.id })
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
    }

    @Test fun pricesAreNumericAndEqualPricesKeepTheirSourceOrder() {
        val rows = listOf(
            record("a", "", 100.0), record("b", "", 9.5), record("c", "", 100.0),
        )
        assertEquals(listOf("b", "a", "c"), sortHistory(rows, HistorySort.PRICE_LOW).map { it.id })
        assertEquals(listOf("a", "c", "b"), sortHistory(rows, HistorySort.PRICE_HIGH).map { it.id })
        assertEquals("9.5", historyAmount(9.5))
        assertEquals("100", historyAmount(100.0))
    }
}
