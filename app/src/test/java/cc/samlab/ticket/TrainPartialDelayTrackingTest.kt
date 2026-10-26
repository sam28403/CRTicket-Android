package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainPhase
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainPartialDelayTrackingTest : StringResourceTest() {
    private fun time(value: String) = LocalDateTime.parse(value)
        .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

    // Controlled times reproduce the reported shape, not G7535's live timetable.
    private fun journey(nextArrivalOffset: Int? = null) = TrainTimeline(
        "G7535", "2026-10-26", listOf(
            TrainStopSchedule("起点", null, "19:00", 0, 0),
            TrainStopSchedule("上海虹桥", "20:00", "20:05", 0, 0),
            TrainStopSchedule("中间站", "20:10", "20:12", 0, 0),
            TrainStopSchedule("终点", "20:20", null, 0),
        ), corrections = listOf(
            null to null, 30 to null, nextArrivalOffset to null, null to null,
        ),
    )

    @Test fun arrivalOnlyDelayPreservesDwellAndFollowingTravelTimes() {
        val timeline = journey()
        assertEquals(time("2026-10-26T20:30"), timeline.stops[1].arrival)
        assertEquals(time("2026-10-26T20:35"), timeline.stops[1].departure)
        assertEquals(time("2026-10-26T20:40"), timeline.stops[2].arrival)
        assertEquals(time("2026-10-26T20:42"), timeline.stops[2].departure)
        assertEquals(time("2026-10-26T20:50"), timeline.end)

        val beforeArrival = timeline.status(time("2026-10-26T20:29"))
        assertEquals(TrainPhase.RUNNING, beforeArrival.phase)
        assertEquals("上海虹桥", beforeArrival.station)
        val dwelling = timeline.status(time("2026-10-26T20:30"))
        assertEquals(TrainPhase.AT_STATION, dwelling.phase)
        assertEquals(time("2026-10-26T20:35"), dwelling.nextEvent)
        val departed = timeline.status(time("2026-10-26T20:35"))
        assertEquals(TrainPhase.RUNNING, departed.phase)
        assertEquals("中间站", departed.station)
        assertEquals(time("2026-10-26T20:40"), departed.nextEvent)
        assertEquals(TrainPhase.RUNNING, timeline.status(time("2026-10-26T20:42")).phase)
        assertEquals(TrainPhase.ARRIVED, timeline.status(timeline.end).phase)
    }

    @Test fun laterValidPredictionCanRecoverTimeWithoutKeepingTheFullDelay() {
        val timeline = journey(nextArrivalOffset = 27)
        assertEquals(time("2026-10-26T20:37"), timeline.stops[2].arrival)
        assertEquals(time("2026-10-26T20:39"), timeline.stops[2].departure)
        assertEquals(time("2026-10-26T20:47"), timeline.end)
    }

    @Test fun arrivalEqualToPreviousDepartureStillNeedsTravelTime() {
        val timeline = journey(nextArrivalOffset = 25)
        assertEquals(time("2026-10-26T20:40"), timeline.stops[2].arrival)
        assertEquals(time("2026-10-26T20:50"), timeline.end)
    }

    @Test fun sixtyMinuteDelayAtAPropagatesToBWithoutPredictions() {
        val timeline = TrainTimeline("G7535", "2026-10-26", listOf(
            TrainStopSchedule("起点", null, "14:00", 0, 0),
            TrainStopSchedule("A", "15:00", "15:06", 0, 0),
            TrainStopSchedule("B", "15:20", "15:24", 0, 0),
            TrainStopSchedule("终点", "15:40", null, 0),
        ), corrections = listOf(
            null to null, 60 to null, null to null, null to null,
        ))

        assertEquals(time("2026-10-26T16:00"), timeline.stops[1].arrival)
        assertEquals(time("2026-10-26T16:06"), timeline.stops[1].departure)
        assertEquals(time("2026-10-26T16:20"), timeline.stops[2].arrival)
        assertEquals(time("2026-10-26T16:24"), timeline.stops[2].departure)
        val approachingB = timeline.status(time("2026-10-26T16:06"))
        assertEquals(TrainPhase.RUNNING, approachingB.phase)
        assertEquals("B", approachingB.station)
        assertEquals(time("2026-10-26T16:20"), approachingB.nextEvent)
        assertEquals(TrainPhase.AT_STATION, timeline.status(time("2026-10-26T16:20")).phase)
        assertEquals(TrainPhase.RUNNING, timeline.status(time("2026-10-26T16:24")).phase)
    }

    @Test fun arrivalOnlyDelayCrossesMidnightWithoutSkippingTheNextLeg() {
        val timeline = TrainTimeline("G7535", "2026-10-26", listOf(
            TrainStopSchedule("起点", null, "23:00", 0, 0),
            TrainStopSchedule("上海虹桥", "23:50", "23:55", 0, 0),
            TrainStopSchedule("终点", "00:10", null, 1),
        ), corrections = listOf(null to null, 20 to null, null to null))

        assertEquals(time("2026-10-27T00:10"), timeline.stops[1].arrival)
        assertEquals(time("2026-10-27T00:15"), timeline.stops[1].departure)
        assertEquals(time("2026-10-27T00:30"), timeline.end)
        val departed = timeline.status(time("2026-10-27T00:15"))
        assertEquals(TrainPhase.RUNNING, departed.phase)
        assertEquals(timeline.end, departed.nextEvent)
    }
}
