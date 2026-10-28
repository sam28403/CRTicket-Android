package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.speedometer.speedometerMaximum
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.ui.sale.SaleScheduleSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class UpstreamNumberingDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var welcome: WelcomeTestState
    private var railfan = false

    @Before fun prepare() {
        welcome = WelcomeTestState(context)
        railfan = RailfanPreferences.enabled(context)
        RailfanPreferences.setEnabled(context, true)
        org.junit.Assume.assumeTrue(context.packageName == "cc.samlab.ticket.ci")
    }

    @After fun restore() {
        RailfanPreferences.setEnabled(context, railfan)
        welcome.close()
    }

    @Test fun nativeQueryAcceptsNewRangesAndRejectsGapsBeforeRequesting() {
        val page = TabletPageFixture(compose)
        try {
            installQueryAdapter(page)
            page.navigate("/train")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("train-query-code").fetchSemanticsNodes().isNotEmpty()
            }
            val accepted = listOf("N1", "N1998", "V5998", "V6001", "V6998", "V7001", "V7998")
            accepted.forEachIndexed { index, train ->
                compose.onNodeWithTag("train-query-code").performTextReplacement(train)
                closeSoftKeyboard()
                compose.onNodeWithTag("train-query-submit").performClick()
                compose.waitUntil(15_000) {
                    page.evaluate("window.numberingRequests.length") == (index + 1).toString() &&
                        compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("\"$train\"", page.evaluate("window.numberingRequests.at(-1)"))
                compose.onNodeWithTag("train-query-submit").assertIsEnabled()
            }
            for (train in listOf("N1999", "N01", "V5999", "V6000", "V6999", "V7000", "V7999")) {
                compose.onNodeWithTag("train-query-code").performTextReplacement(train)
                closeSoftKeyboard()
                compose.onNodeWithTag("train-query-submit").performClick()
                compose.waitForIdle()
                assertEquals(accepted.size.toString(), page.evaluate("window.numberingRequests.length"))
            }
            page.capture("numbering-query-validation")
        } finally {
            page.close()
        }
    }

    @Test fun speedometerAcceptsNAndVAndOpensGuideWithCorrectRange() {
        val page = TabletPageFixture(compose)
        try {
            for ((train, maximum) in listOf("N1998" to 140, "V7998" to 120)) {
                page.navigate("/home")
                page.navigate("/railfan/speedometer")
                compose.onNodeWithTag("speed-train").performTextReplacement(train)
                compose.onNodeWithTag("speed-station-${R.string.ticket_from}")
                    .performTextReplacement("测试起点")
                compose.onNodeWithTag("speed-station-${R.string.ticket_to}")
                    .performTextReplacement("测试终点")
                closeSoftKeyboard()
                compose.onNodeWithText("开始记录").performScrollTo().performClick()
                compose.onNodeWithText("将手机竖直立在车窗旁").assertExists()
                assertEquals(maximum, speedometerMaximum(train))
                page.capture("numbering-speed-$train")
            }
        } finally {
            page.close()
        }
    }

    @Test fun packagedSaleScheduleAndChangelogContainLatestContent() = runBlocking {
        val times = SaleScheduleSource(context).load()
        assertEquals(LocalTime.of(17, 0), times["南通东"])
        val log = context.assets.open("changelogs/0.10.0.md").bufferedReader().use { it.readText() }
        assertTrue(log.contains("相较 0.9.5"))
        assertTrue(log.contains("南通东"))
        assertTrue(log.contains("140 km/h"))
        assertFalse(log.contains("相较 0.9.4"))
    }

    private fun installQueryAdapter(page: TabletPageFixture) {
        val assets = context.assets
        val apiFile = assets.list("web/assets")!!.first { name ->
            name.startsWith("api-") && assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains("withCredentials") }
        }
        page.evaluate("window.numberingResult = ${currentTrainQueryFixture()}")
        page.evaluate("""
            window.numberingReady = false;
            window.numberingRequests = [];
            import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                const original = api.defaults.adapter;
                api.defaults.adapter = async config => {
                    if (config.url !== '/train-info') return original(config);
                    window.numberingRequests.push(config.params.train);
                    return {config, status: 200, statusText: 'OK', headers: {}, data: {
                        ...window.numberingResult, success: true, source: '12306',
                        train: config.params.train, date: config.params.date,
                    }};
                };
                window.numberingReady = true;
            });
        """.trimIndent())
        compose.waitUntil(10_000) { page.evaluate("window.numberingReady") == "true" }
    }
}
