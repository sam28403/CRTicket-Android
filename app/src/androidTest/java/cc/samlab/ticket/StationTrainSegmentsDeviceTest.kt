package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationBoardRow
import cc.samlab.ticket.ui.station.StationBoardState
import cc.samlab.ticket.ui.station.StationTab
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationTrainSegmentsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun liveSegmentNumbersTabsRefreshAndDetailNavigation() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        WelcomeTestState(context).use { verifyLive() }
    }

    private fun verifyLive() {
        val fixture = TabletPageFixture(compose)
        try {
            val delegate = MainActivity::class.java.getDeclaredField("stationBoard\$delegate")
                .apply { isAccessible = true }
                .get(fixture.activity) as Lazy<*>
            lateinit var controller: NativeStationBoardController
            fixture.instrumentation.runOnMainSync {
                controller = delegate.value as NativeStationBoardController
            }
            for ((station, departure) in listOf("南京南" to "G100", "天津西" to "G97", "唐山" to "G97")) {
                val location = "/station?station=$station"
                fixture.navigate("/station")
                fixture.evaluate("window.crticketNavigate(${JSONObject.quote(location)})")
                lateinit var state: StationBoardState
                compose.runOnIdle { state = controller.stateFor(location) }
                compose.waitUntil(120_000) {
                    !state.loading && state.tabs[StationTab.ALL].orEmpty().any(::target)
                }
                compose.onNodeWithTag("station-follow").let {
                    if (state.follow) it.performClick()
                }
                for (tab in listOf(StationTab.ALL, StationTab.ARRIVAL, StationTab.DEPARTURE)) {
                    compose.onNodeWithTag("station-tab-${tab.key}").performClick()
                    val rows = state.tabs.getValue(tab)
                    val index = rows.indexOfFirst(::target)
                    assertTrue("Missing G100/G97 at $station in $tab", index >= 0)
                    val row = rows[index]
                    val expected = if (tab == StationTab.ARRIVAL && station != "唐山") "G100" else departure
                    assertEquals("$station $tab", expected, row.train)
                    assertEquals(expected, android.net.Uri.parse(row.route).getQueryParameter("train"))
                    assertEquals("G100/G97", android.net.Uri.parse(row.route).getQueryParameter("record"))
                    compose.onNodeWithTag("station-board").performScrollToIndex(index)
                    val card = compose.onNodeWithTag("station-card-${row.id}")
                    card.assertIsDisplayed()
                    compose.onNode(
                        hasTestTag("station-train-code") and
                            hasAnyAncestor(hasTestTag("station-card-${row.id}")),
                        useUnmergedTree = true,
                    ).assertIsDisplayed().assertTextEquals(expected)
                    val bounds = card.fetchSemanticsNode().boundsInRoot
                    val grid = compose.onNodeWithTag("station-board").fetchSemanticsNode().boundsInRoot
                    assertTrue("Card exceeds grid", bounds.left >= grid.left && bounds.right <= grid.right + 1)
                    fixture.capture("segments-$station-${tab.key}")
                }
                compose.onNodeWithTag("station-refresh").performClick()
                compose.waitUntil(30_000) { !state.loading }
                assertEquals(departure, state.tabs.getValue(StationTab.DEPARTURE).first(::target).train)
                if (station == "唐山") {
                    val index = state.tabs.getValue(StationTab.DEPARTURE).indexOfFirst(::target)
                    compose.onNodeWithTag("station-board").performScrollToIndex(index)
                    val row = state.tabs.getValue(StationTab.DEPARTURE)[index]
                    compose.onNode(
                        hasTestTag("station-train-code") and
                            hasAnyAncestor(hasTestTag("station-card-${row.id}")),
                        useUnmergedTree = true,
                    ).performClick()
                    compose.waitUntil(15_000) {
                        fixture.evaluate("location.hash.startsWith('#/train?')") == "true"
                    }
                    val queryTrain = fixture.evaluate(
                        "new URLSearchParams(location.hash.split('?')[1]).get('train')",
                    )
                    assertEquals("\"G97\"", queryTrain)
                    compose.waitUntil(120_000) {
                        compose.onAllNodesWithTag("train-query-stop-1").fetchSemanticsNodes().isNotEmpty()
                    }
                    fixture.capture("segments-train-detail")
                }
            }
        } catch (error: Throwable) {
            println("Segment test route: ${fixture.evaluate("location.hash")}")
            fixture.capture("segments-failure")
            throw error
        } finally {
            fixture.close()
        }
    }

    private fun target(row: StationBoardRow) = row.id.startsWith("llt-G100/G97-")
}
