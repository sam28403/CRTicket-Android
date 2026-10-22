package cc.samlab.ticket

import cc.samlab.ticket.arrival.StationVisit
import cc.samlab.ticket.arrival.mergeStationVisits
import org.junit.Assert.assertEquals
import org.junit.Test

class StationVisitMergeTest {
    @Test fun mergingPreservesOtherStationsAndTheEarliestKnownVisit() {
        val local = listOf(StationVisit("北京南", 300), StationVisit("广州南", 400))
        val imported = listOf(
            StationVisit("北京南", 100, true),
            StationVisit("上海虹桥", 200),
            StationVisit("北京南", 500),
        )
        val merged = mergeStationVisits(local, imported)
        assertEquals(listOf(
            StationVisit("广州南", 400), StationVisit("上海虹桥", 200),
            StationVisit("北京南", 100, true),
        ), merged)
        assertEquals(merged, mergeStationVisits(merged, imported))
        assertEquals(merged, mergeStationVisits(merged, emptyList()))
    }

    @Test fun equalTimesPreferAnActualFirstVisitOverAHistoricalReminder() {
        assertEquals(listOf(StationVisit("北京南", 100)), mergeStationVisits(
            listOf(StationVisit("北京南", 100, true)),
            listOf(StationVisit("北京南", 100)),
        ))
    }
}
