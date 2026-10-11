package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainPhase
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class TrainTimelineTest {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()

    private fun journey() = TrainTimeline("1461", "2026-10-10", listOf(
        TrainStopSchedule("北京", null, "11:59", 0),
        TrainStopSchedule("天津西", "13:00", "13:05", 0),
        TrainStopSchedule("上海", "10:00", null, 1),
    ))

    @Test fun followsDepartureArrivalDwellAndDestinationBoundaries() {
        val timeline = journey()
        val waiting = timeline.status(time("2026-10-10T02:58:30Z"))
        assertEquals(TrainPhase.WAITING, waiting.phase)
        assertEquals("1小时1分钟后发车", waiting.text)
        assertEquals(0, waiting.progress)

        val departing = timeline.status(time("2026-10-10T03:59:00Z"))
        assertEquals(TrainPhase.RUNNING, departing.phase)
        assertEquals("前方到站天津西，剩余1小时1分钟", departing.text)
        assertEquals(time("2026-10-10T05:00:00Z"), departing.nextEvent)

        val atStation = timeline.status(time("2026-10-10T05:00:00Z"))
        assertEquals(TrainPhase.AT_STATION, atStation.phase)
        assertEquals("当前到站天津西", atStation.text)

        val leaving = timeline.status(time("2026-10-10T05:05:00Z"))
        assertEquals(TrainPhase.RUNNING, leaving.phase)
        assertEquals("前方到站上海，剩余20小时55分钟", leaving.text)

        val arrived = timeline.status(time("2026-10-11T02:00:00Z"))
        assertEquals(TrainPhase.ARRIVED, arrived.phase)
        assertEquals("列车已经到达目的地", arrived.text)
        assertEquals("上海", arrived.station)
        assertEquals(100, arrived.progress)
        assertEquals(null, arrived.nextEvent)
    }

    @Test fun infersMidnightDepartureDuringStationDwell() {
        val timeline = TrainTimeline("K1", "2026-10-10", listOf(
            TrainStopSchedule("甲", null, "23:00"),
            TrainStopSchedule("乙", "23:58", "00:05", 0),
            TrainStopSchedule("丙", "01:00", null, 1),
        ))
        assertEquals(time("2026-10-10T16:05:00Z"), timeline.stops[1].departure)
        assertEquals(TrainPhase.AT_STATION, timeline.status(time("2026-10-10T16:00:00Z")).phase)
    }

    @Test fun respectsMultipleDaysAndOfflineDepartureDays() {
        val timeline = TrainTimeline("K2", "2026-10-10", listOf(
            TrainStopSchedule("甲", null, "09:00", 0, 0),
            TrainStopSchedule("乙", "23:55", "00:10", 1, 2),
            TrainStopSchedule("丙", "09:00", null, 3),
        ))
        assertEquals(time("2026-10-11T15:55:00Z"), timeline.stops[1].arrival)
        assertEquals(time("2026-10-11T16:10:00Z"), timeline.stops[1].departure)
    }

    @Test fun roundsCountdownUpUntilEventAndHandlesZeroDwell() {
        val timeline = TrainTimeline("G1", "2026-10-10", listOf(
            TrainStopSchedule("甲", null, "09:00"),
            TrainStopSchedule("乙", "09:30", "09:30"),
            TrainStopSchedule("丙", "10:00", null),
        ))
        assertEquals("0小时1分钟后发车", timeline.status(timeline.start - 1).text)
        assertEquals("前方到站丙，剩余0小时30分钟",
            timeline.status(time("2026-10-10T01:30:00Z")).text)
        assertEquals(TrainPhase.ARRIVED, timeline.status(timeline.end + 60_000).phase)
    }

    @Test fun rejectsIncompleteClocksInvalidDaysAndNonChronologicalSchedules() {
        val invalid = listOf(
            listOf(TrainStopSchedule("甲", null, "24:00"), TrainStopSchedule("乙", "10:00", null)),
            listOf(TrainStopSchedule("甲", null, "09:00"), TrainStopSchedule("乙", null, null)),
            listOf(TrainStopSchedule("甲", null, "09:00"), TrainStopSchedule("乙", "10:00", null, -1)),
            listOf(TrainStopSchedule("甲", null, "09:00"), TrainStopSchedule("乙", "08:00", null, 0)),
        )
        for (schedules in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                TrainTimeline("G1", "2026-10-10", schedules)
            }
        }
    }
}
