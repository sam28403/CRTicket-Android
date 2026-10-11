package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import cc.samlab.ticket.tracking.TrainTrackingText
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainCurrentCodeTest {
    @Test fun g97G100ChangesFromG100ToG97AtTianjinWest() {
        val timeline = TrainTimeline("G97/G100", "2026-10-10", listOf(
            TrainStopSchedule("上海虹桥", null, "10:04", train = "G100"),
            TrainStopSchedule("济南西", "13:02", "13:04", train = "G100"),
            TrainStopSchedule("天津西", "14:09", "14:12", train = "G97"),
            TrainStopSchedule("长春西", "19:07", null, train = "G97"),
        ))
        val change = timeline.stops[2].arrival!!
        assertEquals("G100", timeline.currentTrain(change - 1))
        assertEquals("G97", timeline.currentTrain(change))
        assertEquals("G97 · 上海虹桥→长春西", TrainTrackingText.content(timeline, change).title)
    }

    @Test fun followsStationCodesAtTheChangingStationAndKeepsJourneyIdentity() {
        val timeline = TrainTimeline("G97/G100", "2026-10-10", listOf(
            TrainStopSchedule("起点", null, "09:00", train = "G97"),
            TrainStopSchedule("换号站", "10:00", "10:05", train = "G100"),
            TrainStopSchedule("终点", "11:00", null, train = "G100"),
        ))
        val change = timeline.stops[1].arrival!!
        assertEquals("G97", timeline.currentTrain(timeline.start - 1))
        assertEquals("G97", timeline.currentTrain(change - 1))
        assertEquals("G100", timeline.currentTrain(change))
        assertEquals("G100", timeline.currentTrain(timeline.stops[1].departure!!))
        assertEquals("G100", timeline.currentTrain(timeline.end))
        assertEquals("G100 · 起点→终点", TrainTrackingText.content(timeline, change).title)
        assertEquals("G97/G100", timeline.train)
    }

    @Test fun missingStationCodesCarryForwardAndOfflineCombinedCodesAreNotGuessed() {
        val timeline = TrainTimeline("G97/G100", "2026-10-10", listOf(
            TrainStopSchedule("起点", null, "09:00", train = "G97"),
            TrainStopSchedule("中途", "10:00", "10:05"),
            TrainStopSchedule("终点", "11:00", null, train = "G100"),
        ))
        assertEquals("G97", timeline.currentTrain(timeline.stops[1].departure!!))
        val offline = TrainTimeline("G97/G100", "2026-10-10", listOf(
            TrainStopSchedule("起点", null, "09:00", train = "G97/G100"),
            TrainStopSchedule("终点", "11:00", null, train = "G97/G100"),
        ))
        assertEquals("G97/G100", offline.currentTrain(offline.start))
        assertEquals("G97/G100", offline.currentTrain(offline.end))
    }
}
