package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.railfan.historical.historicalTrainInput
import cc.samlab.ticket.ui.railfan.historical.parseHistoricalTimetable
import cc.samlab.ticket.ui.railfan.historical.parseHistoricalVersions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoricalTimetableTest {
    @Test
    fun trainInputKeepsUpToFiveAsciiCharactersAndOneLetter() {
        assertEquals("G1234", historicalTrainInput("g1d2/3４45"))
        assertEquals("12345", historicalTrainInput("123456"))
        assertEquals("1A23", historicalTrainInput("1a2Ｂ3"))
    }

    @Test
    fun unusualStopDataStaysInResponseOrder() {
        val response = JSONObject(
            """
            {
              "train_codes": "1326/1323",
              "stops": [
                {"station_name":"北京西","station_no":"01","start_time":"17:00","mileage":0},
                {"station_name":"思\uE246","station_no":"03","arrive_time":"18:40","start_time":"18:38","mileage":180},
                null,
                {"station_name":"思\uE246","station_no":"03","arrive_time":"18:407","start_time":"19:01","mileage":160},
                {"station_name":"思\uE246","arrive_time":"19:20","start_time":"19:23","mileage":null},
                {"station_name":{},"arrive_time":[],"mileage":"缺失"}
              ]
            }
            """.trimIndent(),
        )
        val timetable = parseHistoricalTimetable(response, "1323")
        val stops = timetable.stops.drop(1)
        assertEquals("1326/1323", timetable.train)
        assertEquals(listOf("思\uE246", "思\uE246", "思\uE246", "—"), stops.map { it.getString("station") })
        assertEquals(listOf("01", "02", "03", "04", "05"), timetable.stops.map { it.getString("no") })
        assertEquals("18:38", stops[0].getString("departure"))
        assertEquals("18:407", stops[1].getString("arrival"))
        assertEquals(listOf("180", "160"), stops.take(2).map { it.getString("mileage") })
        assertTrue(stops[0].isNull("stay"))
        assertTrue(stops[1].isNull("stay"))
        assertEquals("3", stops[2].getString("stay"))
        assertTrue(stops[3].isNull("arrival"))
    }

    @Test
    fun versionsUseUniqueValidDatesNewestFirst() {
        val versions = JSONArray(
            """["2003.11.25",null,"2026.02.30",{},"2026.11.05","2026.11.05","2026-11-05"]""",
        )
        assertEquals(listOf("2026.11.05", "2003.11.25"), parseHistoricalVersions(versions))
    }
}
