package cc.samlab.ticket

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationBoardState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationBoardEditingTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun resultUpdatesPreserveStationBeingEdited() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WelcomeTestState(context).use {
            val fixture = TabletPageFixture(compose)
            try {
                fixture.navigate("/station")
                val delegate = MainActivity::class.java
                    .getDeclaredField("stationBoard\$delegate")
                    .apply { isAccessible = true }
                    .get(fixture.activity) as Lazy<*>
                lateinit var controller: NativeStationBoardController
                lateinit var state: StationBoardState
                compose.runOnIdle {
                    controller = delegate.value as NativeStationBoardController
                    state = controller.stateFor("/station")
                }
                val payload = JSONObject()
                    .put("kind", "board")
                    .put("station", "")
                    .put("resultStation", "北京南")
                    .put("date", "2026-10-30")
                    .put("tabs", JSONObject())
                    .toString()
                fun updateResult() {
                    val revision = state.revision
                    controller.receive(payload)
                    compose.waitUntil(10_000) { state.revision > revision }
                }
                updateResult()
                val input = compose.onNodeWithTag("station-query")
                input.performTextReplacement("shanghai")
                updateResult()
                input.assertTextEquals("shanghai")
                input.performTextReplacement("上海虹桥")
                updateResult()
                input.assertTextEquals("上海虹桥")
                compose.runOnIdle { assertEquals("上海虹桥", state.station) }
                compose.onNodeWithTag("station-submit").performClick()
                compose.waitUntil(10_000) {
                    fixture.evaluate(
                        "new URLSearchParams(location.hash.split('?')[1]).get('station')",
                    ) == JSONObject.quote("上海虹桥")
                }
            } finally {
                fixture.close()
            }
        }
    }
}
