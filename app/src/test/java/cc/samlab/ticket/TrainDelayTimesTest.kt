package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainDelayTimes
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainDelayTimesTest : StringResourceTest() {
    @Test fun readsRealPrimaryMessageAndRejectsUnusableResponses() {
        assertEquals(5, TrainDelayTimes.predictionOffset("19:18", "预计C412次列车，嘉兴站到达时间为19:23"))
        assertEquals(-10, TrainDelayTimes.predictionOffset("20:10", "预计C412次列车，嘉兴站出发时间为20:00"))
        assertEquals(null, TrainDelayTimes.predictionOffset("19:18", "暂无正晚点信息，请稍后查询"))
        assertEquals(null, TrainDelayTimes.predictionOffset("19:18", "请进行验证码验证"))
    }

    @Test fun readsSignedEarlyMinutesAndMissingFallbackFields() {
        assertEquals(-10, TrainDelayTimes.fallbackOffset("--", "-10"))
        assertEquals(4, TrainDelayTimes.fallbackOffset("4", ""))
        assertEquals(0, TrainDelayTimes.fallbackOffset("--", "0"))
        assertEquals(null, TrainDelayTimes.fallbackOffset("--", "--"))
        assertEquals(null, TrainDelayTimes.fallbackOffset("", ""))
    }

    @Test fun selectsNearestDayForMidnightPredictions() {
        assertEquals(7, TrainDelayTimes.nearestOffset("23:58", "00:05"))
        assertEquals(-10, TrainDelayTimes.nearestOffset("00:05", "23:55"))
        assertEquals(LocalDateTime.parse("2026-10-25T00:05"),
            TrainDelayTimes.corrected("2026-10-24", 0, "23:58", 7))
    }

    @Test fun usesActualDwellAndFallsBackForNonPositiveDwell() {
        assertEquals(8, TrainDelayTimes.stay(5, 4, 7))
        assertEquals(5, TrainDelayTimes.stay(5, 10, null))
        assertEquals(5, TrainDelayTimes.stay(5, 5, null))
        assertEquals(15, TrainDelayTimes.stay(5, null, 10))
        assertEquals(null, TrainDelayTimes.stay(null, 4, 7))
    }

    @Test fun trackingKeepsSingleSidedPredictionWithoutAddingADay() {
        val timeline = TrainTimeline("C412", "2026-10-24", listOf(
            TrainStopSchedule("甲", null, "19:00", 0, 0),
            TrainStopSchedule("乙", "20:00", "20:05", 0, 0),
            TrainStopSchedule("丙", "21:00", null, 0),
        ), corrections = listOf(null to 0, 10 to null, 5 to null))
        val stop = timeline.stops[1]
        assertEquals(5 * 60_000L, stop.departure!! - stop.arrival!!)
        assertEquals(125 * 60_000L, timeline.end - timeline.start)
    }

    @Test fun trackingAppliesEarlyArrivalAcrossMidnight() {
        val schedule = listOf(
            TrainStopSchedule("甲", null, "23:00", 0, 0),
            TrainStopSchedule("乙", "00:05", null, 1),
        )
        val planned = TrainTimeline("K1", "2026-10-24", schedule)
        val predicted = TrainTimeline("K1", "2026-10-24", schedule,
            corrections = listOf(null to null, -10 to null))
        assertEquals(planned.end - 10 * 60_000L, predicted.end)
    }
}
