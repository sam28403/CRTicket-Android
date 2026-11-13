package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoricalTrainDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun liveTimetableShowsNonInteractiveStopCards() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("historicalLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousRailfan = RailfanPreferences.enabled(context)
        RailfanPreferences.setEnabled(context, true)
        try {
            val fixture = TabletPageFixture(compose)
            try {
                if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("important-notice-confirm").performClick()
                }
                val title = fixture.activity.getString(R.string.historical_train_title)
                compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
                val date = compose.onNodeWithTag("historical-train-date")
                compose.waitUntil(25_000) { runCatching { date.assertIsEnabled() }.isSuccess }
                compose.onNodeWithText(fixture.activity.getString(R.string.historical_train_hint)).assertIsDisplayed()
                compose.onNodeWithTag("historical-train-code").performTextInput("t27")
                compose.onNodeWithTag("historical-train-submit").performClick()
                val firstStop = compose.onNodeWithTag("train-query-stop-0")
                compose.waitUntil(25_000) { runCatching { firstStop.assertIsDisplayed() }.isSuccess }
                firstStop.assertHasNoClickAction().assert(hasAnyDescendant(hasText("01")))
                compose.onNodeWithTag("train-query-pull").assertExists()
                compose.onNodeWithTag("train-query-refresh").assertIsEnabled().performClick()
                compose.waitUntil(25_000) {
                    runCatching { compose.onNodeWithTag("train-query-refresh").assertIsEnabled() }.isSuccess
                }
                val results = compose.onNodeWithTag("historical-train-results")
                val mapTitle = hasText(fixture.activity.getString(R.string.train_map_title))
                compose.waitUntil(15_000) {
                    runCatching { results.performScrollToNode(mapTitle) }.isSuccess
                }
                compose.onNode(mapTitle).assertIsDisplayed()
                fixture.capture("historical-train-map")
                val mapHint = hasText(fixture.activity.getString(R.string.historical_train_map_disclaimer))
                results.performScrollToNode(mapHint)
                compose.onNode(mapHint).assertIsDisplayed()
                results.performScrollToIndex(0)
                results.performTouchInput { swipeDown() }
                compose.waitUntil(25_000) {
                    runCatching { compose.onNodeWithTag("train-query-refresh").assertIsEnabled() }.isSuccess
                }
                fixture.capture("historical-train-result")
            } finally {
                fixture.close()
            }
        } finally {
            RailfanPreferences.setEnabled(context, previousRailfan)
        }
    }

    @Test
    fun disabledNetworkBlocksHistoricalQueryEvenWithCachedDates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = context.getSharedPreferences("android", 0)
        val cache = context.getSharedPreferences("historical-timetables", 0)
        val previousCache = cache.getString("versions", null)
        val previousNotice = settings.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val previousRailfan = RailfanPreferences.enabled(context)
        val previousNetwork = context.getSharedPreferences("developer-options", 0)
            .getBoolean("networkEnabled", true)
        val previousUpdate = context.getSharedPreferences("developer-options", 0)
            .getBoolean("lltAutoUpdate", true)
        settings.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        RailfanPreferences.setEnabled(context, true)
        cache.edit().putString("versions", """["2010.09.10","2003.11.25"]""").commit()
        instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, false) }
        try {
            val fixture = TabletPageFixture(compose)
            try {
                val title = fixture.activity.getString(R.string.historical_train_title)
                val entry = compose.onNode(hasText(title) and hasClickAction())
                entry.performScrollTo().assertIsNotEnabled()
                fixture.navigate("/railfan/historical-train")
                val message = fixture.activity.getString(R.string.network_features_disabled)
                compose.onNodeWithText(message).assertIsDisplayed()
                compose.onNodeWithTag("historical-train-code").assertDoesNotExist()
                compose.onNodeWithTag("historical-train-date").assertDoesNotExist()
                instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, true) }
                compose.onNodeWithTag("historical-train-code").assertIsDisplayed()
            } finally {
                fixture.close()
            }
        } finally {
            instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, previousNetwork) }
            context.getSharedPreferences("developer-options", 0).edit()
                .putBoolean("lltAutoUpdate", previousUpdate).commit()
            RailfanPreferences.setEnabled(context, previousRailfan)
            settings.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, previousNotice).commit()
            cache.edit().putString("versions", previousCache).commit()
        }
    }
}
