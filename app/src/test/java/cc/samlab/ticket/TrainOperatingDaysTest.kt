package cc.samlab.ticket

import cc.samlab.ticket.ui.trainquery.operatingCalendarCells
import cc.samlab.ticket.ui.trainquery.trainOperatingDays
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class TrainOperatingDaysTest {
    @Test fun distinguishesRunningStoppedAndMissingDates() {
        val days = trainOperatingDays(listOf(
            "20261024" to "1",
            "20261025" to "0",
            "20261026" to "2",
            "20260230" to "1",
        ))
        assertEquals(true, days.dates[LocalDate.of(2026, 10, 24)])
        assertEquals(false, days.dates[LocalDate.of(2026, 10, 25)])
        assertNull(days.dates[LocalDate.of(2026, 10, 26)])
        assertEquals(2, days.dates.size)
        assertEquals(listOf(YearMonth.of(2026, 10)), days.months)
    }

    @Test fun conflictingDatesAreUnknownInsteadOfStopped() {
        val days = trainOperatingDays(listOf("20261024" to "1", "20261024" to "0"))
        assertTrue(days.dates.isEmpty())
    }

    @Test fun alignsMondayAndHandlesLeapYearAndSixWeekMonths() {
        val february = operatingCalendarCells(YearMonth.of(2028, 2))
        assertNull(february[0])
        assertEquals(LocalDate.of(2028, 2, 1), february[1])
        assertEquals(29, february.filterNotNull().size)
        val march = operatingCalendarCells(YearMonth.of(2026, 3))
        assertEquals(42, march.size)
        assertEquals(LocalDate.of(2026, 3, 1), march[6])
        assertEquals(LocalDate.of(2026, 3, 31), march.filterNotNull().last())
    }
}
