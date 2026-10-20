package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainStationChip
import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import cc.samlab.ticket.tracking.TrainTrackingText
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainStationNotificationTest : StringResourceTest() {
    private val timeline = TrainTimeline("K1", "2026-10-10", listOf(
        TrainStopSchedule("北京", null, "21:00"),
        TrainStopSchedule("乌鲁木齐南", "22:00", "23:05"),
        TrainStopSchedule("终点", "00:20", null),
    ))
    private val arrival = timeline.stops[1].arrival!!
    private val departure = timeline.stops[1].departure!!

    @Test fun longStopShowsDepartureCountdownAndDatedBeijingArrivalTime() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val content = TrainTrackingText.content(timeline, arrival)
            assertEquals("K1 · 北京→终点", content.title)
            assertEquals(
                "当前到站：乌鲁木齐南，1h5m后出发\n前方到站：终点，预计到达：10-11 00:20",
                content.text,
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun fiveCharacterStationSlidesThroughItsFullNameWhileBodyStaysComplete() {
        val first = TrainTrackingText.content(timeline, arrival)
        val second = TrainTrackingText.content(timeline, arrival + 3_000)
        val third = TrainTrackingText.content(timeline, arrival + 6_000)
        assertEquals("乌鲁木齐", first.chip)
        assertEquals("鲁木齐南", second.chip)
        assertEquals(first.chip, third.chip)
        assertEquals(first.text, second.text)
        assertEquals(3_000L, TrainStationChip.nextFrameDelay("乌鲁木齐南", arrival, arrival))
        assertEquals(1L, TrainStationChip.nextFrameDelay("乌鲁木齐南", arrival, arrival + 2_999))
    }

    @Test fun shortStationsKeepTheirFullNamesWithoutAnimation() {
        for (station in listOf("济南西", "上海虹桥")) {
            assertEquals(station, TrainStationChip.text(station, arrival, arrival + 9_000))
            assertNull(TrainStationChip.nextFrameDelay(station, arrival, arrival + 9_000))
        }
    }

    @Test fun departingRestoresRunningCountdownExactlyAtDeparture() {
        val before = TrainTrackingText.content(timeline, departure - 1)
        assertEquals(
            "当前到站：乌鲁木齐南，0h1m后出发\n前方到站：终点，预计到达：10-11 00:20",
            before.text,
        )
        val leaving = TrainTrackingText.content(timeline, departure)
        assertEquals("1h15m", leaving.chip)
        assertEquals("前方到站：终点\n剩余1小时15分钟", leaving.text)
    }

    @Test fun midnightChangesArrivalClockToSameDayWithoutChangingItsTimezone() {
        val overnight = TrainTimeline("K2", "2026-10-10", listOf(
            TrainStopSchedule("起点", null, "23:00"),
            TrainStopSchedule("停靠站", "23:50", "00:05"),
            TrainStopSchedule("终点", "01:00", null),
        ))
        val now = Instant.parse("2026-10-10T16:00:00Z").toEpochMilli()
        assertEquals(
            "当前到站：停靠站，0h5m后出发\n前方到站：终点，预计到达：01:00",
            TrainTrackingText.content(overnight, now).text,
        )
    }
}
