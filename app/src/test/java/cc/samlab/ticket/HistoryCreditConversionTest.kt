package cc.samlab.ticket

import cc.samlab.ticket.ui.history.switchHistoryCreditPrice
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCreditConversionTest {
    @Test fun togglingConvertsYuanAndPointsWithoutDecimalDrift() {
        assertEquals("50000", switchHistoryCreditPrice("500", true))
        assertEquals("500", switchHistoryCreditPrice("50000", false))
        assertEquals("29", switchHistoryCreditPrice("0.29", true))
        var amount = "500.01"
        repeat(10) {
            amount = switchHistoryCreditPrice(switchHistoryCreditPrice(amount, true), false)
        }
        assertEquals("500.01", amount)
        assertEquals("", switchHistoryCreditPrice("", true))
        assertEquals("0", switchHistoryCreditPrice("0", false))
    }
}
