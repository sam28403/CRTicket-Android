package cc.samlab.ticket

import cc.samlab.ticket.ui.trainlist.TrainListRow
import cc.samlab.ticket.ui.trainlist.TrainListSort
import cc.samlab.ticket.ui.trainlist.parseTrainListDuration
import cc.samlab.ticket.ui.trainlist.sortTrainList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun durationOrdersByMinutesWithStableTiesAndMissingValuesLast() {
        val durations = listOf(
            "short" to "2:59",
            "long" to "100:00",
            "day" to "25:01",
            "tie" to "25:01",
            "missing" to "--",
            "invalid" to "01:60",
        ).mapIndexed { index, (train, duration) ->
            row(train, index, null, null, null).copy(duration = duration)
        }

        assertEquals(
            listOf("long", "day", "tie", "short", "missing", "invalid"),
            sortTrainList(durations, TrainListSort.DURATION, true).map { it.train },
        )
        assertEquals(
            listOf("short", "day", "tie", "long", "missing", "invalid"),
            sortTrainList(durations, TrainListSort.DURATION, false).map { it.train },
        )
    }

    @Test fun durationParsingHandlesZeroAndRejectsMalformedValues() {
        assertEquals(0.0, parseTrainListDuration("00:00"))
        assertEquals(6000.0, parseTrainListDuration("100:00"))
        for (duration in listOf("", "null", "-1:30", "01:-1", "01:60", "01:x:30")) {
            assertNull(duration, parseTrainListDuration(duration))
        }
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
