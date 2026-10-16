package cc.samlab.ticket

import cc.samlab.ticket.ui.station.StationBoardRow
import cc.samlab.ticket.ui.station.stationCurrentIndex
import cc.samlab.ticket.ui.station.stationBoardTiming
import org.junit.Assert.*
import org.junit.Test

class StationBoardTimeTest {
    private val infinity = Double.POSITIVE_INFINITY
    private fun row(
        arrival: Double = 36000.0,
        departure: Double = 36600.0,
        start: Double = 35700.0,
        end: Double = departure,
    ) = StationBoardRow(
        "id", "G1", "北京南", "上海", "10:00", "10:10", "10 分钟", "/train",
        departure, arrival, departure, start, end, "即将开车",
    )

    @Test fun arrivalAndDepartureBoundariesPreserveAmberPriority() {
        val row = row()
        assertTrue(row.upcoming(35999))
        assertFalse(row.inStation(35999))
        assertTrue(row.inStation(36000))
        assertFalse(row.upcoming(36000))
        assertTrue(row.inStation(36599))
        assertFalse(row.inStation(36600))
        assertFalse(row.upcoming(36600))
    }

    @Test fun originsTerminalsAndOvernightStopsRetainTheirWindows() {
        val origin = row(arrival = infinity)
        assertFalse(origin.inStation(36000))
        assertTrue(origin.upcoming(36000))
        val terminal = row(departure = infinity, end = 36000.0)
        assertTrue(terminal.upcoming(35999))
        assertFalse(terminal.upcoming(36000))
        assertFalse(terminal.inStation(36000))
        assertTrue(row(arrival = -300.0, departure = 300.0).inStation(0))
        assertTrue(row(arrival = 86100.0, departure = 86700.0).inStation(86399))
    }

    @Test fun locatePrefersFirstHighlightThenNextTrainAndHandlesEndOfDay() {
        val rows = listOf(row().copy(time = 36000.0), row().copy(time = 40000.0))
        assertEquals(0, stationCurrentIndex(rows, 35999))
        assertEquals(1, stationCurrentIndex(rows, 37000))
        assertEquals(1, stationCurrentIndex(rows, 86399))
        assertEquals(0, stationCurrentIndex(emptyList(), 0))
    }

    @Test fun unchangedSecondsDoNotInvalidateTheVisibleBoard() {
        val rows = listOf(row())
        assertEquals(stationBoardTiming(rows, 35701), stationBoardTiming(rows, 35759))
        assertNotEquals(stationBoardTiming(rows, 35999), stationBoardTiming(rows, 36000))
        assertNotEquals(stationBoardTiming(rows, 36599), stationBoardTiming(rows, 36600))
    }
}
