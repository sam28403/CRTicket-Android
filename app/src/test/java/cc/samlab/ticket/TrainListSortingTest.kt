package cc.samlab.ticket

import cc.samlab.ticket.ui.trainlist.TrainListRow
import cc.samlab.ticket.ui.trainlist.TrainListSort
import cc.samlab.ticket.ui.trainlist.sortTrainList
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainListSortingTest {
    private val rows = listOf(
        row("G2", 0, 10.0, 100.04, 20.0),
        row("G10", 1, 2.0, 100.01, 3.0),
        row("K1", 2, 2.0, 90.0, 3.0),
        row("K2", 3, null, null, null),
        row("L1", 4, null, null, null),
    )

    @Test fun numericOrderingKeepsTiesStableAndMissingValuesLastInBothDirections() {
        for (sort in listOf(TrainListSort.MILEAGE, TrainListSort.STATIONS)) {
            assertEquals(
                listOf("G10", "K1", "G2", "K2", "L1"),
                sorted(sort, false),
            )
            assertEquals(
                listOf("G2", "G10", "K1", "K2", "L1"),
                sorted(sort, true),
            )
        }
    }

    @Test fun speedUsesUnroundedValuesRatherThanTheIdenticalDisplayText() {
        assertEquals(listOf("K1", "G10", "G2", "K2", "L1"), sorted(TrainListSort.SPEED, false))
        assertEquals(listOf("G2", "G10", "K1", "K2", "L1"), sorted(TrainListSort.SPEED, true))
    }

    @Test fun returningToTrainOrderPreservesTheSourceNaturalCategoryAndNumberOrder() {
        assertEquals(rows.map { it.train }, sorted(TrainListSort.TRAIN, false))
        assertEquals(rows.reversed().map { it.train }, sorted(TrainListSort.TRAIN, true))
        assertEquals(listOf("G2", "G10", "K1", "K2", "L1"), rows.map { it.train })
    }

    private fun sorted(sort: TrainListSort, descending: Boolean) =
        sortTrainList(rows, sort, descending).map { it.train }

    private fun row(
        train: String,
        index: Int,
        mileage: Double?,
        speed: Double?,
        stations: Double?,
    ) = TrainListRow(
        train, "A", "B", "01:00", stations.toString(), mileage.toString(), "100.0",
        index, mileage, speed, stations,
    )
}
