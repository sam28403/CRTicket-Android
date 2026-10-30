package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.tracking.TrainTrackingDelayUpdate
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TrainTrackingDelayUpdateTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun payload() = JSONObject("""{
        "train":"G1","date":"2026-10-30","delayEnabled":true,
        "delayCacheDay":"2026-10-30",
        "stops":[
            {"station":"A","departure":"10:00","departureDay":0},
            {"station":"B","arrival":"11:00","departure":"11:05",
             "arrivalDay":0,"departureDay":0,"stay":5},
            {"station":"C","arrival":"12:00","arrivalDay":0}
        ]
    }""")

    private fun delayed(): JSONObject {
        val data = payload()
        data.put("scheduledStops", org.json.JSONArray(data.getJSONArray("stops").toString()))
        data.getJSONArray("stops").getJSONObject(1)
            .put("arrivalOffset", 30).put("departureOffset", 35)
        data.getJSONArray("stops").getJSONObject(2).put("arrivalOffset", 35)
        return TrainTrackingDelayUpdate.merge(data.toString(), data, today)
    }

    @Test fun failedRefreshKeepsPredictionsAndAllowsAnotherRefresh() {
        val old = delayed()
        val failed = TrainTrackingDelayUpdate.merge(old.toString(), null, today)
        assertTrue(failed.getBoolean("delayEnabled"))
        assertEquals(TrainTrackingStore.parse(old.toString()).end,
            TrainTrackingStore.parse(failed.toString()).end)
        val response = payload()
        response.getJSONArray("stops").getJSONObject(1).put("arrivalOffset", 40)
        val recovered = TrainTrackingDelayUpdate.merge(failed.toString(), response, today)
        assertEquals(40, recovered.getJSONArray("stops").getJSONObject(1).getInt("arrivalOffset"))
    }

    @Test fun punctualArrivalReplacesDelayAndMissingDepartureKeepsItsPrediction() {
        val response = payload()
        response.getJSONArray("stops").getJSONObject(1).put("arrivalOffset", 0)
        val updated = TrainTrackingDelayUpdate.merge(delayed().toString(), response, today)
        val row = updated.getJSONArray("stops").getJSONObject(1)
        assertEquals(0, row.getInt("arrivalOffset"))
        assertEquals(35, row.getInt("departureOffset"))
        assertEquals("11:00", row.getString("arrival"))
        assertEquals("11:40", row.getString("departure"))
    }

    @Test fun emptyRefreshDoesNotDisableFuturePredictions() {
        val updated = TrainTrackingDelayUpdate.merge(payload().toString(), payload(), today)
        assertTrue(updated.getBoolean("delayEnabled"))
        assertFalse(updated.getBoolean("delayAvailable"))
    }

    @Test fun recoveredPunctualityMovesTheTrackingArrivalBackToSchedule() {
        val old = delayed()
        val response = payload()
        response.getJSONArray("stops").getJSONObject(1)
            .put("arrivalOffset", 0).put("departureOffset", 0)
        response.getJSONArray("stops").getJSONObject(2).put("arrivalOffset", 0)
        val updated = TrainTrackingDelayUpdate.merge(old.toString(), response, today)
        val before = TrainTrackingStore.parse(old.toString())
        val after = TrainTrackingStore.parse(updated.toString())
        assertEquals(35 * 60_000L, before.end - after.end)
        assertEquals(TrainTrackingStore.parse(payload().toString()).end, after.end)
    }

    @Test fun midnightExpiresCachedPredictionsWithoutDisablingRefresh() {
        val updated = TrainTrackingDelayUpdate.merge(delayed().toString(), null, today.plusDays(1))
        assertTrue(updated.getBoolean("delayEnabled"))
        assertFalse(updated.getBoolean("delayAvailable"))
        assertEquals(TrainTrackingStore.parse(payload().toString()).end,
            TrainTrackingStore.parse(updated.toString()).end)
    }
}
