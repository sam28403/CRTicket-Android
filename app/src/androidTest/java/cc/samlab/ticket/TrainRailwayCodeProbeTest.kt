package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingStore
import cc.samlab.ticket.tracking.TrainTrackingText
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrainRailwayCodeProbeTest {
    @Test fun inspectG97AndG100StationTrainCodesFromPhoneQueries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        try {
            fixture.open()
            val readyDeadline = System.currentTimeMillis() + 20_000
            while (fixture.evaluate("Boolean(window.crticketApiResult)") != "true" &&
                System.currentTimeMillis() < readyDeadline) Thread.sleep(200)
            val module = context.assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && name.endsWith(".js") && context.assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains(" as requestRailway") }
            }
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            fixture.evaluate("""
                window.codeProbe = null;
                import('${MainActivity.ORIGIN}/assets/$module').then(async module => {
                    const results = [];
                    for (const train of ['G97', 'G100']) {
                        try {
                            const data = await module.requestRailway('/train-info?train=' + train + '&date=$date');
                            results.push({ query: train, source: data.source, stops: data.stops.map(stop => ({
                                station: stop.station, train: stop.train,
                                arrival: stop.arrival, departure: stop.departure,
                            })) });
                        } catch (error) { results.push({ query: train, error: error.message }); }
                    }
                    window.codeProbe = { results };
                }).catch(error => window.codeProbe = { error: error.message });
            """.trimIndent())
            var result = "null"
            val deadline = System.currentTimeMillis() + 120_000
            while (result == "null" && System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
                result = fixture.evaluate("window.codeProbe")
            }
            val data = JSONObject(result)
            val output = File(context.getExternalFilesDir(null), "train-code-probe.json")
            output.writeText(data.toString(2))
            println("Station train code probe: $data")
            assertTrue("No timetable results: $result", data.has("results"))
            val records = data.getJSONArray("results")
            for (index in 0 until records.length()) {
                val record = records.getJSONObject(index)
                assertEquals("12306", record.optString("source"))
                val timeline = TrainTrackingStore.parse(JSONObject()
                    .put("train", record.getString("query"))
                    .put("date", date.toString())
                    .put("stops", record.getJSONArray("stops")).toString())
                val change = timeline.stops.first { it.station == "天津西" }.arrival!!
                assertEquals("G100", timeline.currentTrain(change - 1))
                assertEquals("G97", timeline.currentTrain(change))
                assertEquals("G97 · 上海虹桥→长春西", TrainTrackingText.content(timeline, change).title)
            }
        } finally {
            fixture.restore()
        }
    }
}
