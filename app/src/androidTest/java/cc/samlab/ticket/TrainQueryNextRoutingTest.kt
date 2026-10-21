package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import cc.samlab.ticket.ui.railfan.RailfanPreferences

@RunWith(AndroidJUnit4::class)
class TrainQueryNextRoutingTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun nextAssignmentBelowMapAndAbsentWithoutRouting() {
        val fixture = TabletPageFixture(compose)
        val previousEnabled = RailfanPreferences.enabled(fixture.activity)
        try {
            fixture.instrumentation.runOnMainSync {
                RailfanPreferences.setEnabled(fixture.activity, true)
            }
            fixture.navigate("/train")
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onAllNodesWithTag("train-query-screen").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            val result = currentTrainQueryFixture()
            val next = JSONObject().put("train", "G1956/7")
                .put("from", "上海虹桥").put("to", "太原南")
                .put("duration", "09:42").put("mileage", 1630).put("stationCount", 19)
                .put("detailTrain", "G1956/G1957").put("clickable", true)
            val snapshot = JSONObject().put("location", "/train")
                .put("train", "G1").put("date", result.getString("date"))
                .put("loading", false).put("result", result).put("nextRouting", next)
                .put("route", JSONObject().put("points", org.json.JSONArray())
                    .put("missingStations", org.json.JSONArray()))
            fun publish() {
                fixture.evaluate("window.CRTicketAndroid.trainQueryResult(${JSONObject.quote(snapshot.toString())})")
                compose.waitForIdle()
            }
            publish()
            compose.onNodeWithTag("train-query-content")
                .performScrollToNode(hasTestTag("train-query-next-routing"))
            compose.onNodeWithText(fixture.activity.getString(R.string.train_query_next_routing))
                .assertIsDisplayed()
            compose.onNodeWithTag("train-card:G1956/7").assertIsEnabled()
            compose.onNodeWithTag("routing-disclaimer").assertIsDisplayed()
            fixture.capture("train-query-next-routing")

            next.put("train", "DJ1234").put("clickable", false)
            publish()
            compose.onNodeWithTag("train-card:DJ1234", useUnmergedTree = true).assertIsNotEnabled()
            fixture.instrumentation.runOnMainSync {
                RailfanPreferences.setEnabled(fixture.activity, false)
            }
            compose.onNodeWithTag("train-query-next-routing").assertDoesNotExist()
            compose.onNodeWithTag("routing-disclaimer").assertDoesNotExist()
            fixture.instrumentation.runOnMainSync {
                RailfanPreferences.setEnabled(fixture.activity, true)
            }
            compose.onNodeWithTag("train-query-next-routing").assertExists()
            snapshot.remove("nextRouting")
            publish()
            compose.onNodeWithTag("train-query-next-routing").assertDoesNotExist()
            compose.onNodeWithTag("routing-disclaimer").assertDoesNotExist()
        } finally {
            fixture.instrumentation.runOnMainSync {
                RailfanPreferences.setEnabled(fixture.activity, previousEnabled)
            }
            fixture.close()
        }
    }
}
