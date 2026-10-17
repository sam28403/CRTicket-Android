package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import cc.samlab.ticket.ui.trainquery.QueryStopHighlight
import cc.samlab.ticket.ui.trainquery.trainQueryTiming
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class TrainQueryTimingTest {
    private val timeline = TrainTimeline("G1", "2026-10-17", listOf(
        TrainStopSchedule("始发", null, "23:00"),
        TrainStopSchedule("中间", "23:50", "00:10"),
        TrainStopSchedule("中间", "01:00", "01:05"),
        TrainStopSchedule("终到", "02:00", null),
    ))

    private fun at(value: String) = ZonedDateTime.parse(value + "+08:00").toInstant().toEpochMilli()

    @Test fun nextStopAndDwellUseAbsoluteTimesAcrossMidnight() {
        val running = trainQueryTiming(timeline.stops, at("2026-10-17T23:30:00"))
        assertEquals(QueryStopHighlight.NONE, running.highlight(0))
        assertEquals(QueryStopHighlight.UPCOMING, running.highlight(1))
        val dwell = trainQueryTiming(timeline.stops, at("2026-10-18T00:00:00"))
        assertEquals(QueryStopHighlight.IN_STATION, dwell.highlight(1))
        assertEquals(QueryStopHighlight.UPCOMING, dwell.highlight(2))
        val departure = trainQueryTiming(timeline.stops, at("2026-10-18T00:10:00"))
        assertEquals(QueryStopHighlight.NONE, departure.highlight(1))
        assertEquals(QueryStopHighlight.UPCOMING, departure.highlight(2))
    }

    @Test fun beforeDepartureAndAfterArrivalHaveNoHighlight() {
        for (time in listOf("2026-10-17T22:59:59", "2026-10-18T02:00:00", "2026-10-19T02:00:00")) {
            val timing = trainQueryTiming(timeline.stops, at(time))
            assertEquals(-1, timing.inStation)
            assertEquals(-1, timing.upcoming)
        }
        val arrival = trainQueryTiming(timeline.stops, at("2026-10-17T23:50:00"))
        assertEquals(1, arrival.inStation)
        assertEquals(2, arrival.upcoming)
    }
}
