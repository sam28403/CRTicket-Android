package cc.samlab.ticket

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.VehicleQueryResults
import cc.samlab.ticket.ui.railfan.VehicleSearch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryEmptyStateDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun wakeDevice() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
    }

    @Test fun trainFailureRetryAndEmptyResult() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.prepareEmptyStateResponses()
            fixture.navigate("/train")
            fixture.evaluate("window.crticketNavigate('/train?source=llt')")
            compose.waitUntil(10_000) {
                fixture.evaluate("location.hash.includes('source=llt')") == "true"
            }
            awaitTag("train-query-code")
            compose.onNodeWithTag("train-query-code").performTextReplacement("g1")
            compose.onNodeWithTag("train-query-code").assertTextContains("G1")
            compose.onNodeWithTag("train-query-submit").performClick()
            awaitTag("train-query-empty")
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry))
                .assertIsDisplayed()
            compose.onNodeWithTag("train-query-summary").assertDoesNotExist()
            fixture.capture("train-query-failure")

            fixture.evaluate("window.emptyStateMode = 'empty'")
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).performClick()
            compose.waitUntil(10_000) { fixture.evaluate("window.emptyStateRequests") == "2" }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(fixture.activity.getString(R.string.train_query_empty))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("train-query-empty").assertIsDisplayed()
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).assertDoesNotExist()
            compose.onNodeWithText(fixture.activity.getString(R.string.train_query_hint)).assertDoesNotExist()
            fixture.capture("train-query-no-results")
        } finally {
            fixture.restoreEmptyStateHistory()
            fixture.close()
        }
    }

    @Test fun stationFailureRetryAndEmptyMapTab() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.prepareEmptyStateResponses()
            fixture.navigate("/station")
            awaitTag("station-query")
            compose.onNodeWithTag("station-query").performTextReplacement("北京南")
            compose.onNodeWithTag("station-submit").performClick()
            awaitTag("station-query-error")
            compose.onNodeWithTag("station-discovery").assertDoesNotExist()
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).assertIsDisplayed()
            fixture.capture("station-query-failure")

            fixture.evaluate("window.emptyStateMode = 'empty'")
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).performClick()
            awaitTag("station-no-trains")
            assertEquals("2", fixture.evaluate("window.emptyStateRequests"))
            compose.onNodeWithTag("station-query-error").assertDoesNotExist()
            compose.onNodeWithText(fixture.activity.getString(R.string.station_board_empty))
                .assertIsDisplayed()
            fixture.capture("station-query-no-results")
            compose.onNodeWithTag("station-tab-map").performClick()
            compose.onNodeWithTag("station-no-trains").assertDoesNotExist()
            compose.onNodeWithTag("station-map").assertIsDisplayed()
            fixture.capture("station-map-no-results")
            compose.onNodeWithTag("station-tab-all").performClick()
            compose.onNodeWithTag("station-no-trains").assertIsDisplayed()
        } finally {
            fixture.restoreEmptyStateHistory()
            fixture.close()
        }
    }

    @Test fun vehicleFailureRetryAndEmptyResultPresentation() {
        val fixture = TabletPageFixture(compose)
        val failed = mutableStateOf(true)
        var retries = 0
        try {
            fixture.instrumentation.runOnMainSync {
                fixture.activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                    MaterialTheme {
                        VehicleQueryResults(
                            query = VehicleSearch("G1", true),
                            rows = emptyList(),
                            trainRows = emptyMap(),
                            loading = false,
                            refreshing = false,
                            error = failed.value,
                            modifier = Modifier.fillMaxSize(),
                            onRefresh = {},
                            onRetry = { retries++; failed.value = false },
                            onVehicle = {},
                            onNavigate = {},
                        )
                    }
                }
            }
            awaitTag("vehicle-query-empty")
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_error))
                .assertIsDisplayed()
            fixture.capture("vehicle-query-failure")
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).performClick()
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_empty))
                .assertIsDisplayed()
            compose.onNodeWithText(fixture.activity.getString(R.string.action_retry)).assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, retries) }
            fixture.capture("vehicle-query-no-results")
        } finally {
            fixture.close()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) {
            runCatching {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
