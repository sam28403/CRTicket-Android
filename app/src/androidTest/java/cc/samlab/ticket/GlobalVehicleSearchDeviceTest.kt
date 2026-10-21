package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalVehicleSearchDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun railfanGateLatestVehicleNavigationAndPullRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val enabled = RailfanPreferences.enabled(context)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val history = context.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE)
        val saved = history.getString("history", null)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        RailfanPreferences.setEnabled(context, false)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            val results = compose.onNodeWithTag("global-search-results")
            fixture.evaluate("""
                window.heldTrainSearch = null;
                window.holdTrainSearch = event => {
                    if (event.detail?.query !== 'G1') return;
                    event.stopImmediatePropagation();
                    window.heldTrainSearch = event.detail;
                };
                window.addEventListener('crticket-search-request', window.holdTrainSearch, true);
            """.trimIndent())
            field.performTextReplacement("G1")
            field.performImeAction()
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            RailfanPreferences.setEnabled(context, true)
            compose.waitUntil(10_000) {
                fixture.evaluate("Boolean(window.heldTrainSearch)") == "true"
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            compose.onNodeWithTag("global-vehicle-latest").assertDoesNotExist()
            fixture.evaluate("""
                window.removeEventListener('crticket-search-request', window.holdTrainSearch, true);
                window.dispatchEvent(new CustomEvent('crticket-search-request', {
                    detail: window.heldTrainSearch
                }));
            """.trimIndent())
            awaitAssignment()
            results.performScrollToNode(hasTestTag("global-vehicle-search"))
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_search_assignment, "G1"))
                .assertIsDisplayed()
            compose.waitUntil(35_000) {
                compose.onAllNodesWithTag("global-vehicle-latest").fetchSemanticsNodes().isNotEmpty()
            }
            val latest = runBlocking { VehicleQueryRepository.search(VehicleSearch.forTrain("G1")!!).first() }
            results.performScrollToNode(hasTestTag("global-vehicle-latest"))
            compose.onNodeWithTag("global-vehicle-latest").assertIsDisplayed()
            compose.onAllNodesWithText(latest.displayVehicle).assertCountEquals(1)
            fixture.capture("global-vehicle-preview")
            results.performScrollToNode(hasTestTag("global-vehicle-search"))
            compose.onNodeWithTag("global-vehicle-search").performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("vehicle-search").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithTag("vehicle-search").assertTextContains("G1")
            awaitHistory()
            compose.onNodeWithTag("vehicle-history").performTouchInput {
                down(androidx.compose.ui.geometry.Offset(centerX, height * 0.1f))
                moveTo(androidx.compose.ui.geometry.Offset(centerX, height * 0.5f), delayMillis = 300)
                moveTo(androidx.compose.ui.geometry.Offset(centerX, height * 0.95f), delayMillis = 300)
            }
            val release = fixture.activity.getString(R.string.refresh_release)
            compose.onNodeWithContentDescription(release).assertIsDisplayed()
            fixture.capture("vehicle-pull-release")
            compose.onNodeWithTag("vehicle-history").performTouchInput { up() }
            val refreshing = fixture.activity.getString(R.string.vehicle_query_refreshing)
            compose.onNodeWithTag("vehicle-history").assertIsDisplayed()
            compose.waitUntil(35_000) {
                compose.onAllNodesWithContentDescription(refreshing).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("vehicle-search").assertTextContains("G1")
            fixture.capture("vehicle-pull-refreshed")
            fixture.navigate("/search")
            field.performTextReplacement(latest.vehicle)
            field.performImeAction()
            awaitAssignment()
            compose.waitUntil(35_000) {
                compose.onAllNodesWithTag("global-vehicle-latest").fetchSemanticsNodes().isNotEmpty()
            }
            results.performScrollToNode(hasTestTag("global-vehicle-latest"))
            compose.onAllNodesWithTag("global-vehicle-latest").assertCountEquals(1)
            compose.onNodeWithTag("global-vehicle-latest")
                .onChildren().assertCountEquals(1)
            compose.onAllNodesWithText(latest.displayVehicle).assertCountEquals(1)
            fixture.capture("global-vehicle-train-preview")
            results.performScrollToNode(hasTestTag("global-vehicle-search"))
            compose.onNodeWithTag("global-vehicle-search").performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("vehicle-search").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithTag("vehicle-search").assertTextContains(latest.vehicle)
            awaitHistory()
            fixture.navigate("/search")
            field.performTextReplacement("G")
            field.performImeAction()
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            field.performTextReplacement("G1/G2")
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            field.performTextReplacement("K123")
            field.performImeAction()
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            field.performTextReplacement("S1")
            field.performImeAction()
            awaitAssignment()
            results.performScrollToNode(hasTestTag("global-vehicle-search"))
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_search_assignment, "S1"))
                .assertIsDisplayed()
            RailfanPreferences.setEnabled(context, false)
            compose.waitUntil {
                compose.onAllNodesWithTag("global-vehicle-search").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            fixture.close()
            RailfanPreferences.setEnabled(context, enabled)
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
            history.edit().apply {
                if (saved == null) remove("history") else putString("history", saved)
            }.commit()
        }
    }

    private fun awaitHistory() {
        compose.waitUntil(35_000) {
            compose.onAllNodesWithTag("vehicle-history").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitAssignment() {
        compose.waitUntil(35_000) {
            compose.onAllNodesWithTag("global-vehicle-search").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
