package cc.samlab.ticket

import cc.samlab.ticket.tracking.TrainStopSchedule
import cc.samlab.ticket.tracking.TrainTimeline
import cc.samlab.ticket.tracking.TrainTrackingText
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrainTrackingTextTest : StringResourceTest() {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()

    private fun journey() = TrainTimeline("G1", "2026-10-10", listOf(
        TrainStopSchedule("北京南", null, "09:00"),
        TrainStopSchedule("济南西", "10:00", "10:05"),
        TrainStopSchedule("上海虹桥", "11:05", null),
    ))

    @Test fun formatsMinuteAndHourChipsWithoutPadding() {
        val cases = mapOf(
            0L to "0m",
            1L to "1m",
            33 * 60_000L to "33m",
            60 * 60_000L to "1h",
            65 * 60_000L to "1h5m",
            119 * 60_000L to "1h59m",
            24 * 60 * 60_000L to "24h",
            -1L to "0m",
        )
        cases.forEach { (milliseconds, expected) ->
            assertEquals(expected, TrainTrackingText.compactDuration(milliseconds))
        }
    }

    @Test fun runningChipCountsDownToNextArrival() {
        val content = TrainTrackingText.content(journey(), time("2026-10-10T01:27:00Z"))
        assertEquals("33m", content.chip)
        assertEquals("G1 · 北京南→上海虹桥", content.title)
        assertEquals("前方到站：济南西\n剩余0小时33分钟", content.text)
        assertFalse(content.text.contains("推算"))
        assertFalse(content.text.contains("2026"))
    }

    @Test fun stationChipShowsCurrentStationAndTextCountsDownToDeparture() {
        val content = TrainTrackingText.content(journey(), time("2026-10-10T02:00:00Z"))
        // Departure is five minutes away; arrival at the next station is 65 minutes away.
        assertEquals("济南西", content.chip)
        assertEquals("G1 · 北京南→上海虹桥", content.title)
        assertEquals("当前到站：济南西，0h5m后出发\n前方到站：上海虹桥，预计到达：11:05", content.text)
    }

    @Test fun waitingChipUsesDepartureCountdown() {
        val content = TrainTrackingText.content(journey(), time("2026-10-10T00:27:00Z"))
        assertEquals("33m", content.chip)
        assertEquals("G1 · 北京南→上海虹桥", content.title)
        assertEquals("等待发车 · 33m后发车", content.text)
    }

    @Test fun finishedJourneysKeepDestinationAndNeverShowNegativeCountdowns() {
        val timeline = journey()
        for (now in listOf(timeline.end, timeline.end + 86400000)) {
            val content = TrainTrackingText.content(timeline, now)
            assertEquals("G1 · 北京南→上海虹桥", content.title)
            assertEquals("已达终点：上海虹桥", content.text)
            assertEquals("已达终点", content.chip)
        }
    }

    @Test fun allPhasesPreserveTheWholeJourneyRoute() {
        val timeline = journey()
        val moments = listOf(
            timeline.start - 60_000,
            timeline.start,
            timeline.stops[1].arrival!!,
            timeline.end,
        )
        for (now in moments) {
            assertEquals("北京南→上海虹桥", TrainTrackingText.content(timeline, now).route)
            assertEquals("G1 · 北京南→上海虹桥", TrainTrackingText.content(timeline, now).title)
        }
    }

    @Test fun departureCountdownSwitchesUnitsAtOneHourAndAboveTwentyFourHours() {
        val cases = mapOf(
            0L to "0m",
            1L to "1m",
            33 * 60_000L to "33m",
            59 * 60_000L to "59m",
            3_599_999L to "60m",
            60 * 60_000L to "1h0m",
            65 * 60_000L to "1h5m",
            1439 * 60_000L to "23h59m",
            1440 * 60_000L to "24h0m",
            86_400_001L to "1d0h",
            1441 * 60_000L to "1d0h",
            1500 * 60_000L to "1d1h",
            2945 * 60_000L to "2d1h",
            -1L to "0m",
        )
        for ((milliseconds, expected) in cases) {
            assertEquals(expected, TrainTrackingText.departureCountdown(milliseconds))
        }
    }

    @Test fun waitingNotificationAndChipUseTheSameCountdownDaysBeforeDeparture() {
        val timeline = journey()
        for (delay in listOf(2945 * 60_000L, 1440 * 60_000L, 65 * 60_000L, 33 * 60_000L)) {
            val content = TrainTrackingText.content(timeline, timeline.start - delay)
            val countdown = TrainTrackingText.departureCountdown(delay)
            assertEquals(countdown, content.chip)
            assertEquals("等待发车 · ${countdown}后发车", content.text)
        }
    }
}
