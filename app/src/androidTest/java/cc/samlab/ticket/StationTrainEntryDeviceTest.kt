package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationBoardState
import cc.samlab.ticket.ui.station.StationTab
import cc.samlab.ticket.ui.trainquery.NativeTrainQueryController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationTrainEntryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun shanghaiSouthG3244StaysSelectedOnlineAndOffline() {
        WelcomeTestState(InstrumentationRegistry.getInstrumentation().targetContext).use {
            val fixture = TabletPageFixture(compose)
            try {
                lateinit var board: NativeStationBoardController
                lateinit var query: NativeTrainQueryController
                lateinit var state: StationBoardState
                fixture.instrumentation.runOnMainSync {
                    fun controller(name: String): Any = (MainActivity::class.java
                        .getDeclaredField("$name\$delegate").apply { isAccessible = true }
                        .get(fixture.activity) as Lazy<*>).value!!
                    board = controller("stationBoard") as NativeStationBoardController
                    query = controller("trainQuery") as NativeTrainQueryController
                }
                fixture.navigate("/station")
                fixture.evaluate("window.crticketNavigate('/station?station=上海南')")
                compose.runOnIdle { state = board.stateFor("/station?station=上海南") }
                compose.waitUntil(120_000) {
                    !state.loading && state.tabs[StationTab.ARRIVAL].orEmpty()
                        .any { row -> row.id.startsWith("llt-G3241/G3244-") }
                }
                if (state.follow) compose.onNodeWithTag("station-follow").performClick()
                compose.onNodeWithTag("station-tab-arrival").performClick()
                val rows = state.tabs.getValue(StationTab.ARRIVAL)
                val index = rows.indexOfFirst { row -> row.id.startsWith("llt-G3241/G3244-") }
                assertEquals("G3244", rows[index].train)
                compose.onNodeWithTag("station-board").performScrollToIndex(index)
                fixture.capture("entry-shanghai-south-G3244")
                compose.onNode(
                    hasTestTag("station-train-code") and
                        hasAnyAncestor(hasTestTag("station-card-${rows[index].id}")),
                    useUnmergedTree = true,
                ).performClick()
                for ((step, offline) in listOf(false, true, false).withIndex()) {
                    compose.waitUntil(120_000) {
                        query.state.optBoolean("offline") == offline &&
                            !query.state.optBoolean("loading") &&
                            query.state.optJSONObject("result")?.optJSONArray("stops")?.length()?.let { it > 0 } == true
                    }
                    assertEquals("G3244", query.state.optString("train"))
                    assertEquals("G3244", query.state.getJSONObject("result").getString("train"))
                    compose.onNodeWithTag("train-query-code").assert(hasText("G3244"))
                    compose.onNodeWithTag("train-query-content").performScrollToIndex(0)
                    compose.onNode(hasText("G3244") and hasAnyAncestor(hasTestTag("train-query-summary")))
                        .assertIsDisplayed()
                    fixture.capture("entry-G3244-${if (offline) "offline" else "online"}")
                    if (step < 2) {
                        compose.onNodeWithText(if (offline) "切换为在线查询" else "切换为路路通离线")
                            .performClick()
                    }
                }
            } finally {
                fixture.close()
            }
        }
    }
}
