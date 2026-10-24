package cc.samlab.ticket

import android.graphics.Bitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.station.*
import cc.samlab.ticket.tracking.StationDelayRepository
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationDelayBoardDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun delayTimesAndRefreshKeepEachTabPosition() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/station")
            lateinit var state: StationBoardState
            var requests = 0
            instrumentation.runOnMainSync {
                val activity = fixture.activity
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                state = StationBoardState("嘉兴") { _, action, station ->
                    assertEquals("refresh", action)
                    assertEquals("嘉兴", station)
                    requests++
                    state.loading = true
                }
                state.date = "2026-10-24"
                state.resultStation = "嘉兴"
                state.follow = false
                state.tabs = tabs(0)
                activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    ExpressiveStationBoardScreen(state, false, false, {}, {}, true)
                }
            }
            for (tab in listOf(StationTab.ALL, StationTab.DEPARTURE, StationTab.ARRIVAL)) {
                compose.runOnIdle {
                    state.selectedTab = tab
                    state.gridState.requestScrollToItem(25, 19)
                }
                compose.waitUntil(10_000) { state.gridState.firstVisibleItemIndex == 25 }
                val before = state.gridState.firstVisibleItemScrollOffset
                compose.onNodeWithTag("station-refresh").performClick()
                compose.runOnIdle {
                    state.tabs = tabs(requests)
                    state.loading = false
                    state.revision++
                }
                compose.waitForIdle()
                assertEquals(tab, state.selectedTab)
                assertEquals(25, state.gridState.firstVisibleItemIndex)
                assertEquals(before, state.gridState.firstVisibleItemScrollOffset)
                assertFalse(state.follow)
                if (tab == StationTab.ALL) {
                    compose.onAllNodesWithText("20:00 (+7')").onFirst().assertIsDisplayed()
                } else {
                    val departure = tab == StationTab.DEPARTURE
                    val time = compose.onAllNodesWithText(
                        if (departure) "20:05" else "20:00", useUnmergedTree = true,
                    ).onFirst()
                    val offset = compose.onAllNodesWithText(
                        if (departure) "(+9')" else "(+7')", useUnmergedTree = true,
                    ).onFirst()
                    time.assertIsDisplayed()
                    offset.assertIsDisplayed()
                    val clockBounds = time.fetchSemanticsNode().boundsInRoot
                    val offsetBounds = offset.fetchSemanticsNode().boundsInRoot
                    assertTrue(offsetBounds.bottom <= clockBounds.top + 1f)
                    assertEquals(clockBounds.right, offsetBounds.right, 1f)
                }
                capture("station-delay-${tab.key}")
            }
            assertEquals(3, requests)
        } finally {
            fixture.close()
        }
    }

    @Test fun liveJiaxingDelayUsesTheStationQueryBridgeData() {
        val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        val payload = JSONObject().put("station", "嘉兴").put("date", date)
            .put("trains", JSONArray().put(JSONObject()
                .put("id", "C412").put("queryTrain", "C412").put("trainDate", date)
                .put("arrival", "19:18").put("departure", "19:21")))
        val rows = StationDelayRepository.query(payload.toString())
        assertEquals(1, rows.length())
        val row = rows.getJSONObject(0)
        assertTrue("No live prediction: $row", row.has("arrivalOffset") || row.has("departureOffset"))
        File(instrumentation.targetContext.getExternalFilesDir(null), "station-delay-live.json")
            .writeText(rows.toString())
    }

    private fun tabs(version: Int): Map<StationTab, List<StationBoardRow>> {
        val rows = List(100) { index ->
            StationBoardRow(
                id = "row-$index", train = "C${index + 1}", from = "甲站", to = "乙站",
                arrival = if (version > 0) "20:00 (+7')" else "19:53",
                departure = if (version > 0) "20:05 (+9')" else "19:56",
                stay = "5 分钟", route = "/train", time = 72000.0,
                arrivalSeconds = 72000.0, departureSeconds = 72300.0,
                highlightStart = 0.0, highlightEnd = 0.0, highlightLabel = "",
                arrivalOffset = if (version > 0) 7 else 0,
                departureOffset = if (version > 0) 9 else 0,
            )
        }
        return listOf(StationTab.ALL, StationTab.DEPARTURE, StationTab.ARRIVAL).associateWith { rows }
    }

    private fun capture(name: String) {
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "station-delay-tests").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
