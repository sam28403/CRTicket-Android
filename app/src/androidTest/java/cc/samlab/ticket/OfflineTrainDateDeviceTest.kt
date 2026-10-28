package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class OfflineTrainDateDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun listEntriesKeepUnrestrictedOfflineDatesAndPickerSelection() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val welcome = WelcomeTestState(context)
        val page = TabletPageFixture(compose)
        try {
            installAdapter(page)
            val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            val dates = listOf(
                "1890-01-01", "2200-12-31", today.minusDays(3).toString(),
                today.plusDays(15).toString(), today.toString(),
            )
            for (date in dates) {
                page.evaluate("CRTicketAndroid.setNativePage('/list')")
                page.evaluate("""
                    CRTicketAndroid.trainListResult(JSON.stringify({
                        date: '$date', loading: false, error: '', result: {trains: [{
                            train: 'G1', from: '北京南', to: '上海虹桥', duration: '04:00',
                            stationCount: 4, mileage: 1000
                        }]}
                    }));
                    window.offlineDateRequests = [];
                """.trimIndent())
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("train-card:G1").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("train-card:G1").performClick()
                val source = if (date == today.toString()) "/train-info" else "/llt-trains/detail"
                compose.waitUntil(15_000) {
                    page.evaluate("window.offlineDateRequests.length") == "1" &&
                        compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("\"$source:$date\"", page.evaluate("window.offlineDateRequests[0]"))
                compose.onNodeWithTag("train-query-date").performClick()
                compose.onNodeWithText("确定").performClick()
                page.capture("offline-date-$date")
                compose.onNodeWithTag("train-query-submit").performClick()
                compose.waitUntil(15_000) { page.evaluate("window.offlineDateRequests.length") == "2" }
                assertEquals("\"$source:$date\"", page.evaluate("window.offlineDateRequests[1]"))
            }
        } finally {
            page.close()
            welcome.close()
        }
    }

    private fun installAdapter(page: TabletPageFixture) {
        val assets = page.instrumentation.targetContext.assets
        val apiFile = assets.list("web/assets")!!.first { name ->
            name.startsWith("api-") && assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains("withCredentials") }
        }
        page.evaluate("window.offlineDateResult = ${currentTrainQueryFixture()}")
        page.evaluate("""
            window.offlineDateReady = false;
            window.offlineDateRequests = [];
            import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                const original = api.defaults.adapter;
                api.defaults.adapter = async config => {
                    if (!['/train-info', '/llt-trains/detail'].includes(config.url)) return original(config);
                    window.offlineDateRequests.push(config.url + ':' + config.params.date);
                    return {config, status: 200, statusText: 'OK', headers: {}, data: {
                        ...window.offlineDateResult, success: true,
                        source: config.url === '/train-info' ? '12306' : '路路通',
                        train: config.params.train, date: config.params.date,
                    }};
                };
                window.offlineDateReady = true;
            });
        """.trimIndent())
        compose.waitUntil(10_000) { page.evaluate("window.offlineDateReady") == "true" }
    }
}
