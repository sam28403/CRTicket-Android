package cc.samlab.ticket

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrainQueryOnlineEntryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun fullRecordQueriesOnlineFirstAndFallsBackToExactOfflineRecord() {
        val fixture = TabletPageFixture(compose)
        try {
            val assets = fixture.instrumentation.targetContext.assets
            val apiFile = assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            fixture.evaluate("window.onlineEntryFixture = ${currentTrainQueryFixture()}")
            fixture.evaluate("""
                window.onlineEntryReady = false;
                window.onlineEntryRequests = [];
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    const original = api.defaults.adapter;
                    api.defaults.adapter = async config => {
                        if (!['/train-info', '/llt-trains/detail'].includes(config.url)) {
                            return original(config);
                        }
                        window.onlineEntryRequests.push(config.url + ':' + config.params.train);
                        if (config.url === '/train-info' && config.params.train === 'D1') {
                            throw new Error('Online fixture unavailable');
                        }
                        return {
                            config, status: 200, statusText: 'OK', headers: {},
                            data: {...window.onlineEntryFixture, success: true,
                                source: config.url === '/train-info' ? '12306' : '路路通',
                                train: config.params.train, date: config.params.date},
                        };
                    };
                    window.onlineEntryReady = true;
                });
            """.trimIndent())
            compose.waitUntil(10_000) { fixture.evaluate("window.onlineEntryReady") == "true" }
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            fixture.evaluate("window.crticketNavigate('/train?train=G1%2FG2B&date=$date')")
            compose.waitUntil(10_000) {
                fixture.evaluate("location.hash.includes('train=G1&')") == "true" &&
                    fixture.evaluate("window.onlineEntryRequests.length") == "1"
            }
            assertEquals("[\"/train-info:G1\"]", fixture.evaluate("window.onlineEntryRequests"))
            fixture.evaluate("window.crticketNavigate('/train?train=D1%2FD2B&date=$date')")
            compose.waitUntil(10_000) {
                fixture.evaluate("window.onlineEntryRequests.length") == "3" &&
                    fixture.evaluate("location.hash.includes('source=llt')") == "true"
            }
            assertEquals(
                "[\"/train-info:G1\",\"/train-info:D1\",\"/llt-trains/detail:D1/D2B\"]",
                fixture.evaluate("window.onlineEntryRequests"),
            )
        } finally {
            fixture.close()
        }
    }
}
