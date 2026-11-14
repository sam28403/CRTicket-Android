package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.ui.railfan.VehicleQueryRepository
import cc.samlab.ticket.ui.railfan.VehicleSearch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalNumericSearchDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun numericVehicleResultsCoexistWithTrainsAndOpenDetails() {
        val expected = runBlocking { VehicleQueryRepository.search(VehicleSearch.parse("5033")!!) }
        assertTrue("The live vehicle query must return 5033 records", expected.isNotEmpty())
        val fixture = TabletPageFixture(compose)
        val railfanEnabled = RailfanPreferences.enabled(fixture.activity)
        try {
            fixture.instrumentation.runOnMainSync { RailfanPreferences.setEnabled(fixture.activity, true) }
            fixture.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            val results = compose.onNodeWithTag("global-search-results")
            field.performTextReplacement("5033")
            field.performImeAction()
            compose.waitUntil(60_000) {
                runCatching { results.performScrollToIndex(0) }
                compose.onAllNodesWithTag("search-trains-title").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.capture("global-5033-trains")
            compose.waitUntil(60_000) {
                runCatching {
                    results.performScrollToNode(hasTestTag("global-vehicle-search"))
                }.isSuccess
            }
            results.performScrollToNode(hasTestTag("global-vehicle-latest"))
            compose.onAllNodesWithText(expected.first().displayVehicle).onFirst().assertExists()
            fixture.capture("global-5033-vehicle")
            results.performScrollToNode(hasTestTag("global-vehicle-search"))
            compose.onNodeWithTag("global-vehicle-search").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("vehicle-search").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("vehicle-search").assertTextContains("5033")
            compose.waitUntil(40_000) {
                compose.onAllNodesWithTag("vehicle-history").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.capture("global-5033-details")
        } finally {
            fixture.instrumentation.runOnMainSync {
                RailfanPreferences.setEnabled(fixture.activity, railfanEnabled)
            }
            fixture.capture("global-5033-final")
            fixture.close()
        }
    }
}
