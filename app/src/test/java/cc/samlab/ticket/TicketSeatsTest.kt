package cc.samlab.ticket

import cc.samlab.ticket.monitor.TicketSeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketSeatsTest {
    private fun seats(detail: String, second: String = "有", standing: String = "有") =
        MutableList(40) { "" }.apply {
            this[30] = second
            this[26] = standing
            this[39] = detail
        }.let(TicketSeats::parse)

    @Test fun preservesTruncatedSeatsAndExactStandingCounts() {
        assertEquals(">20", seats("O055300021")["second"])
        assertEquals("28", seats("O055300028")["second"])
        assertEquals("21", seats("O055303021")["standing"])
        assertEquals("0", seats("", second = "无")["second"])
    }

    @Test fun ambiguousOrContradictoryDetailsKeepOfficialAvailability() {
        assertEquals(">20", seats("O055300028O055300030")["second"])
        assertEquals(">20", seats("O055300000")["second"])
        assertEquals(">20", seats("malformed")["second"])
        assertEquals("3", seats("O055300028", second = "3")["second"])
    }

    @Test fun detectsOnlyKnownPositiveAvailability() {
        for (value in listOf("有", ">20", "1", "999")) assertTrue(TicketSeats.available(value))
        for (value in listOf("无", "0", "--", "", "候补", "-1")) assertFalse(TicketSeats.available(value))
    }
}
