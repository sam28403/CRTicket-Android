package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorClient
import cc.samlab.ticket.monitor.TicketMonitorConfig
import cc.samlab.ticket.monitor.TicketMonitorMatches
import cc.samlab.ticket.monitor.TicketMonitorNotifications
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TicketMonitorDataTest {
    @Test fun upstreamRowsKeepStationNamesAndSeatCounts() {
        val fields = MutableList(56) { "" }
        fields[1] = "<span>预订</span>"
        fields[2] = "240000G0970A"
        fields[3] = "G97"
        fields[6] = "VNP"
        fields[7] = "AOH"
        fields[8] = "09:00"
        fields[9] = "13:30"
        fields[10] = "04:30"
        fields[11] = "Y"
        fields[26] = "无"
        fields[30] = "3"
        fields[31] = "无"
        fields[32] = "有"
        val data = JSONObject()
            .put("map", JSONObject().put("VNP", "北京南").put("AOH", "上海虹桥"))
            .put("result", JSONArray().put(fields.joinToString("|")))

        val row = TicketMonitorClient.parseTrains(data).getJSONObject(0)
        assertEquals("240000G0970A-VNP-AOH", row.getString("id"))
        assertEquals("北京南", row.getString("from"))
        assertEquals("上海虹桥", row.getString("to"))
        assertEquals("预订", row.getString("status"))
        assertTrue(row.getBoolean("canBuy"))
        assertEquals("3", row.getJSONObject("seats").getString("second"))
        assertEquals("0", row.getJSONObject("seats").getString("first"))
        assertEquals(">20", row.getJSONObject("seats").getString("business"))
        assertEquals("0", row.getJSONObject("seats").getString("standing"))
        assertEquals("--", row.getJSONObject("seats").getString("softSleeper"))
    }

    @Test fun availabilityChangesAlertOnceAndRespectSeatAndTrainFilters() {
        val configJson = JSONObject(MonitorTestPayloads.tickets())
        configJson.put("filters", JSONObject()
            .put("trainFilters", JSONArray().put(" g97 "))
            .put("seatFilters", JSONArray().put("second"))
            .put("selectedDepartureStations", JSONArray().put("北京南"))
            .put("selectedArrivalStations", JSONArray().put("上海虹桥"))
            .put("timeRange", JSONArray().put(8).put(12)))
        val config = TicketMonitorConfig.parse(configJson.toString())
        val state = JSONObject().put("queriedAt", "2026-10-13T12:00:00Z")
        val rows = JSONArray().put(row("G97", "3"))

        val first = TicketMonitorMatches.collect(state, rows, config)
        assertEquals(1, first.size)
        assertTrue(first.single().contains("二等座：3"))
        assertTrue(TicketMonitorMatches.collect(state, rows, config).isEmpty())
        rows.getJSONObject(0).getJSONObject("seats").put("second", "2")
        assertEquals(1, TicketMonitorMatches.collect(state, rows, config).size)
        assertEquals(2, state.getJSONArray("events").length())
        assertTrue(TicketMonitorMatches.collect(state, JSONArray().put(row("G98", "3")), config).isEmpty())
        assertTrue(TicketMonitorMatches.collect(state, JSONArray().put(row("G97", "0")), config).isEmpty())
        assertEquals(1, TicketMonitorMatches.collect(state, JSONArray().put(row("G97", "2")), config).size)
        assertEquals(3, state.getJSONArray("events").length())
        assertTrue(TicketMonitorMatches.collect(state,
            JSONArray().put(row("G97", "2").put("canBuy", false)), config).isEmpty())
    }

    @Test fun trainTypeAndTimeFiltersApplyBeforeAvailabilityAlerts() {
        val filter = JSONObject().put("highSpeedOnly", true)
            .put("timeRange", JSONArray().put(8).put(12))
        assertTrue(TicketMonitorMatches.matches(row("G97", "3"), filter))
        assertFalse(TicketMonitorMatches.matches(row("K97", "3"), filter))
        assertFalse(TicketMonitorMatches.matches(row("D101", "3"), filter))
        assertFalse(TicketMonitorMatches.matches(row("G97", "3").put("departure", "12:00"), filter))
        filter.put("highSpeedOnly", false).put("ordinaryOnly", true)
        assertTrue(TicketMonitorMatches.matches(row("D101", "3"), filter))
        assertFalse(TicketMonitorMatches.matches(row("G97", "3"), filter))
    }

    @Test fun stationFiltersMatchTheSameNamesShownInQueryResults() {
        val filters = JSONObject()
            .put("selectedDepartureStations", JSONArray().put("北京南"))
            .put("selectedArrivalStations", JSONArray().put("上海虹桥"))
        assertTrue(TicketMonitorMatches.matches(row("G97", "3"), filters))
        assertFalse(TicketMonitorMatches.matches(row("G97", "3").put("from", "北京"), filters))
        assertFalse(TicketMonitorMatches.matches(row("G97", "3").put("to", "上海"), filters))
    }

    @Test fun changingNativeFiltersClearsPreviousSignaturesAndAlertHistory() {
        val fixture = MonitorLifecycleDeviceFixture(InstrumentationRegistry.getInstrumentation())
        fixture.withSnapshot {
            fixture.onMain { TicketMonitorService.stop(fixture.context) }
            fixture.await("Original ticket service did not stop") {
                fixture.notice(TicketMonitorNotifications.ID) == null
            }
            val config = JSONObject(MonitorTestPayloads.tickets())
            fixture.onMain {
                TicketMonitorStore.save(fixture.context, JSONObject()
                    .put("active", true)
                    .put("config", config)
                    .put("previous", JSONObject().put("old-train", "old-signature"))
                    .put("events", JSONArray().put(JSONObject().put("text", "旧提醒"))))
                config.getJSONObject("filters").put("seatFilters", JSONArray().put("business"))
                TicketMonitorService.update(fixture.context, config.toString())
            }
            val changed = TicketMonitorStore.read(fixture.context)
            assertTrue(changed.getBoolean("active"))
            assertEquals(0, changed.getJSONObject("previous").length())
            assertEquals(0, changed.getJSONArray("events").length())
            assertEquals("business", changed.getJSONObject("config").getJSONObject("filters")
                .getJSONArray("seatFilters").getString(0))
        }
    }

    private fun row(train: String, second: String): JSONObject = JSONObject()
        .put("id", "$train-VNP-AOH")
        .put("train", train)
        .put("from", "北京南")
        .put("to", "上海虹桥")
        .put("departure", "09:00")
        .put("canBuy", true)
        .put("seats", JSONObject().put("second", second).put("first", "5"))
}
