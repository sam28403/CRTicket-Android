package cc.samlab.ticket

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneBackendTest {
    @Test fun timetableDownloadsAndRunsEntirelyOnPhone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity

        fun evaluate(script: String): String {
            val completed = CountDownLatch(1)
            var result = "null"
            instrumentation.runOnMainSync {
                activity.webView.evaluateJavascript(script) {
                    result = it
                    completed.countDown()
                }
            }
            assertTrue(completed.await(10, TimeUnit.SECONDS))
            return result
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (evaluate("Boolean(window.crticketApiResult)") != "true" && System.nanoTime() < deadline) {
            Thread.sleep(200)
        }
        assertEquals("true", evaluate("Boolean(window.crticketApiResult)"))
        assertEquals("true", evaluate("JSON.parse(CRTicketAndroid.configuration()).backend === 'phone'"))
        val module = context.assets.list("web/assets")!!.first { name ->
            name.endsWith(".js") && context.assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains(" as requestRailway") }
        }
        val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
        evaluate("""
            window.phoneProbe = null;
            import('${MainActivity.ORIGIN}/assets/$module').then(module => module.requestRailway('/llt-trains?date=$date'))
                .then(data => window.phoneProbe = { success: data.success, version: data.version, total: data.total })
                .catch(error => window.phoneProbe = { success: false, error: error.message });
        """.trimIndent())
        val downloadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        var result = "null"
        while (result == "null" && System.nanoTime() < downloadDeadline) {
            Thread.sleep(250)
            result = evaluate("window.phoneProbe")
        }
        assertTrue("Phone timetable result: $result", JSONObject(result).optBoolean("success"))
        assertTrue(JSONObject(result).optInt("total") > 0)
        println("Phone timetable: $result")

        evaluate("""
            window.searchProbe = null;
            const previousCallback = window.crticketApiResult;
            window.crticketApiResult = (id, result) => {
                if (id === 'phone-search-probe') window.searchProbe = { status: result.status, error: result.error };
                else previousCallback(id, result);
            };
            CRTicketAndroid.request('phone-search-probe', 'GET',
                'https://search.12306.cn/search/v1/train/search?keyword=G1&date=${date.toString().replace("-", "")}', '');
        """.trimIndent())
        val searchDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50)
        var search = "null"
        while (search == "null" && System.nanoTime() < searchDeadline) {
            Thread.sleep(250)
            search = evaluate("window.searchProbe")
        }
        println("Phone 12306 search: $search")
        assertEquals("Phone 12306 connectivity: $search", 200, JSONObject(search).optInt("status"))

        evaluate("""
            window.trainProbe = null;
            import('${MainActivity.ORIGIN}/assets/$module')
                .then(module => module.requestRailway('/train-info?train=G1&date=$date'))
                .then(data => window.trainProbe = { source: data.source, stops: data.stops.length })
                .catch(error => window.trainProbe = { error: error.message });
        """.trimIndent())
        val trainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        var train = "null"
        while (train == "null" && System.nanoTime() < trainDeadline) {
            Thread.sleep(250)
            train = evaluate("window.trainProbe")
        }
        println("Phone G1 query: $train")
        assertTrue("Phone G1 query: $train", JSONObject(train).optInt("stops") > 0)
        instrumentation.runOnMainSync { activity.finish() }
    }
}
