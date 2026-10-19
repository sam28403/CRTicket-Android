package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationDiscoveryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun discoveryHistoryNavigationAndNoServiceState() {
        val fixture = TabletPageFixture(compose)
        val saved = fixture.evaluate("localStorage.getItem('crticket-station-query-history')")
        try {
            fixture.evaluate("""
                localStorage.setItem('crticket-station-query-history', JSON.stringify(['北京南','上海虹桥']));
            """.trimIndent())
            val assets = fixture.instrumentation.targetContext.assets
            val apiFile = assets.list("web/assets")!!.first { name ->
                name.startsWith("api-") && assets.open("web/assets/$name")
                    .bufferedReader().use { it.readText().contains("withCredentials") }
            }
            fixture.evaluate("""
                window.stationDiscoveryReady = false;
                import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
                    const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
                    api.defaults.adapter = async config => {
                        const data = config.url === '/station/discovery'
                            ? {success: true, date: config.params.date,
                               stations: ['广州南','杭州东','南京南','郑州东'].map((station, i) => ({
                                   station, total: 100 - i, origin: 20, terminal: 10,
                                   passingWindows: [{arrival: 0, departure: 86400}],
                                   completionTimes: ['00:00','01:00']
                               }))}
                            : {success: true, station: config.params.station, date: config.params.date,
                               total: 0, trains: [], source: '路路通', generatedAt: new Date().toISOString()};
                        return {status: 200, statusText: 'OK', headers: {}, config, data};
                    };
                    window.stationDiscoveryReady = true;
                });
            """.trimIndent())
            compose.waitUntil(10_000) { fixture.evaluate("window.stationDiscoveryReady") == "true" }
            fixture.navigate("/station")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("共 100 趟").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("important-notice-confirm").performClick()
            }
            compose.onNodeWithTag("station-query-history").assertIsDisplayed()
            compose.onAllNodesWithText("已接发：2 / 100，在站过路车：1").onFirst().assertIsDisplayed()
            val before = compose.onNodeWithTag("station-busy-title").fetchSemanticsNode().boundsInWindow.top
            compose.onNodeWithTag("station-clear-history").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("station-query-history").fetchSemanticsNodes().isEmpty()
            }
            compose.waitForIdle()
            val after = compose.onNodeWithTag("station-busy-title").fetchSemanticsNode().boundsInWindow.top
            assertTrue("History should collapse", after < before)
            assertEquals("null", fixture.evaluate("localStorage.getItem('crticket-station-query-history')"))
            fixture.capture("station-discovery-busy")
            compose.onNodeWithTag("station-discovery")
                .performScrollToNode(hasTestTag("station-recommendations-title"))
            compose.onNodeWithText("今日随机推荐").assertIsDisplayed()
            fixture.capture("station-discovery-random")
            compose.onNodeWithTag("station-discovery").performScrollToIndex(0)
            compose.onAllNodes(hasClickAction() and hasText("广州南", substring = true))
                .onFirst().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("station-no-trains").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("今日暂无车次").assertIsDisplayed()
            fixture.capture("station-discovery-empty")
            pressBack()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("station-query-history").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("station-query-history").assertIsDisplayed()
        } finally {
            fixture.evaluate("""
                if ($saved === null) localStorage.removeItem('crticket-station-query-history');
                else localStorage.setItem('crticket-station-query-history', $saved);
            """.trimIndent())
            fixture.close()
        }
    }
}
