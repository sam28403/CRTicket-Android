package cc.samlab.ticket

import cc.samlab.ticket.speedometer.*
import org.junit.Assert.*
import org.junit.Test

class SpeedRecordingTest {
    @Test fun maximumFollowsTrainTypeAndCBoundaries() {
        mapOf("G1" to 350, "D1" to 250, "C1" to 160, "C1000" to 160,
            "C1001" to 350, "C4000" to 350, "C4001" to 160, "C4700" to 160,
            "C4701" to 350, "C9999" to 350, "S1" to 200, "Z1" to 160,
            "N1" to 140, "N1998" to 140, "n1" to 140,
            "V1" to 120, "V7998" to 120, "v6501" to 120, "Y1" to 120,
            "T1" to 300, "K1" to 250, "X1" to 400, "x9998" to 400,
            "L1" to 120, "1234" to 120,
        ).forEach { (train, maximum) -> assertEquals(train, maximum, speedometerMaximum(train)) }
    }

    @Test fun newRecordTrainNumberFollowsSpeedometerRules() {
        listOf("X1", "x12", "X123", "X9998", "Z1", "z1998", "T1", "K1", "G1", "N1998", "V7998").forEach {
            assertTrue(it, isSpeedometerTrainValid(it))
        }
        listOf(
            "", "L1", "l9999", "1234", "59999", "X", "X0", "X0001", "X9999", "X10000", "X+1",
            "Z", "Z0", "Z0001", "Z1999", "Z2000", "Z10000", "N1999", "V7999",
        ).forEach {
            assertFalse(it, isSpeedometerTrainValid(it))
        }
    }

    @Test fun signalGapConnectsStraightLineAndCountsDistance() {
        val record = journey().append(SpeedPoint(0.0, 0.0, 1000, 0))
            .append(SpeedPoint(0.0, 0.01, 61_000, 0))
        assertEquals(1111.95, record.meters, 0.1)
        assertEquals(2, record.points.size)
        assertEquals(66.717, record.copy(elapsed = 60_000).average, 0.01)
    }

    @Test fun pauseDoesNotCountMovementBetweenSegments() {
        val record = journey().append(SpeedPoint(0.0, 0.0, 1000, 0))
            .append(SpeedPoint(0.0, 1.0, 61_000, 1))
            .append(SpeedPoint(0.0, 1.01, 62_000, 1))
        assertEquals(1111.95, record.meters, 0.1)
    }

    @Test fun staleFixDoesNotChangeDistanceOrTrack() {
        val record = journey().append(SpeedPoint(0.0, 0.0, 1000, 0))
        assertEquals(record, record.append(SpeedPoint(0.0, 1.0, 999, 0)))
        assertEquals(0.0, record.average, 0.0)
    }

    @Test fun elapsedTimeIncludesSecondsAndLongJourneys() {
        assertEquals("00:00:00", recordingTime(0))
        assertEquals("01:01:01", recordingTime(3_661_000))
        assertEquals("25:00:00", recordingTime(90_000_000))
    }

    @Test fun recordingsRequireAtLeastOneMinuteOfActiveTime() {
        assertFalse(journey().copy(elapsed = 0).canSave)
        assertFalse(journey().copy(elapsed = 59_999).canSave)
        assertTrue(journey().copy(elapsed = 60_000).canSave)
        assertTrue(journey().copy(elapsed = 60_001).canSave)
    }

    private fun journey() = SpeedRecording("test", "G1", "北京南", "上海虹桥", 0)
}
