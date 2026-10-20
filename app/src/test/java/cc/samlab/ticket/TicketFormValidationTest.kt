package cc.samlab.ticket

import cc.samlab.ticket.ui.history.acceptsTicketNumberInput
import cc.samlab.ticket.ui.history.ticketDistanceError
import cc.samlab.ticket.ui.history.ticketPriceError
import cc.samlab.ticket.ui.history.validateTicketFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketFormValidationTest : StringResourceTest() {
    private val valid = mapOf(
        "number" to "E123456789", "trainNo" to "G25",
        "from" to "北京南", "to" to "上海虹桥",
        "date" to "2026年10月19日", "time" to "08:00",
        "price" to "553.5", "distance" to "", "seatType" to "二等座", "seatNo" to "03车12A号",
        "sellPlace" to "北京南站",
    )

    @Test fun reportsEveryInvalidFieldTogether() {
        val invalid = valid + mapOf(
            "trainNo" to "bad", "from" to "  ", "price" to "-1",
            "distance" to "Infinity", "date" to "2026-02-29", "time" to "24:00",
        )
        assertEquals(
            setOf("trainNo", "from", "price", "distance", "date", "time"),
            validateTicketFields(invalid).keys,
        )
        assertEquals(10, validateTicketFields(emptyMap()).size)
    }

    @Test fun acceptsValidFareOptionalDistanceAndExistingDateFormats() {
        for (date in listOf("2026年10月19日", "2026-10-19", "2028/02/29")) {
            assertTrue(validateTicketFields(valid + ("date" to date)).isEmpty())
        }
    }

    @Test fun validatesFareStepsPointsEndingsAndPositiveIntegerDistance() {
        for (price in listOf("0.5", ".5", "1", "12.5", "12.50", "12.0", "10000000")) {
            assertNull(price, ticketPriceError(price, false))
        }
        for (price in listOf("0", "0.0", "-1", "12.1", "12.51", "1e2", "10000000.5")) {
            assertTrue(price, ticketPriceError(price, false) != null)
        }
        for (points in listOf("50", "100", "150", "55350")) {
            assertNull(points, ticketPriceError(points, true))
        }
        for (points in listOf("0", "1", "51", "100.5", "100.0", "-50")) {
            assertTrue(points, ticketPriceError(points, true) != null)
        }
        for (distance in listOf("", "1", "1318")) {
            assertNull(distance, ticketDistanceError(distance))
        }
        for (distance in listOf("0", "-1", "1.0", "1.5", "1e2", "1,000", " ")) {
            assertTrue(distance, ticketDistanceError(distance) != null)
        }
        assertEquals(setOf("sellPlace"), validateTicketFields(valid + ("sellPlace" to " ")).keys)
        assertEquals(setOf("price"), validateTicketFields(valid, credit = true).keys)
        assertTrue(validateTicketFields(valid + ("price" to "55350"), credit = true).isEmpty())
    }

    @Test fun numericInputRejectsPunctuationSpacesAndSignsIncludingPastedText() {
        for (value in listOf("", "0", "123")) {
            assertTrue(acceptsTicketNumberInput(value, decimal = false))
            assertTrue(acceptsTicketNumberInput(value, decimal = true))
        }
        for (value in listOf("1,000", "1、2", "1 2", "-12", "+12", "1e2", "１２", "12\n")) {
            assertFalse(value, acceptsTicketNumberInput(value, decimal = false))
            assertFalse(value, acceptsTicketNumberInput(value, decimal = true))
        }
        assertTrue(acceptsTicketNumberInput("12.5", decimal = true))
        assertFalse(acceptsTicketNumberInput("12.5", decimal = false))
        assertFalse(acceptsTicketNumberInput("1.2.3", decimal = true))
    }

    @Test fun correctedFieldsNoLongerReportErrorsAndInvalidEditsRemainErrors() {
        assertTrue(validateTicketFields(valid).isEmpty())
        for (price in listOf("", " ", "NaN", "Infinity", "-1", "abc")) {
            assertEquals(setOf("price"), validateTicketFields(valid + ("price" to price)).keys)
        }
        for (train in listOf("g25", "1461", "50001")) {
            assertTrue(validateTicketFields(valid + ("trainNo" to train)).isEmpty())
        }
    }

    @Test fun reportsLocalStorageLimitsOnTheirCorrespondingInputs() {
        val invalid = valid + mapOf(
            "number" to "x".repeat(201), "sellPlace" to "x".repeat(201),
            "gate" to "x".repeat(201), "message" to "x".repeat(2001),
            "price" to "10000001", "distance" to "10000001",
        )
        assertEquals(
            setOf("number", "sellPlace", "gate", "message", "price", "distance"),
            validateTicketFields(invalid).keys,
        )
    }
}
