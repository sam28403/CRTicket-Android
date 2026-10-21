package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.readVehicleCarouselCatalog
import cc.samlab.ticket.ui.railfan.VehicleSearch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleQueryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun liveSearchBothDirectionsAndCapturePreview() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val models = readVehicleCarouselCatalog(target)
        assertEquals(307, models.size)
        assertTrue(models.any { it.name == "CR200J1-A" })
        assertTrue(models.any { it.name == "CR200J1-C" })
        assertTrue(models.any { it.name.contains("天驰") })
        val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)
        val previous = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val recent = target.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE)
        val savedHistory = recent.getString("history", null)
        recent.edit().remove("history").commit()
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/railfan/vehicle")
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("vehicle-search").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_explore)).assertIsDisplayed()
            compose.onNodeWithTag("vehicle-history-empty").assertIsDisplayed()
            compose.onNodeWithTag("vehicle-head-carousel").assertIsDisplayed()
            fixture.capture("vehicle-query-welcome")
            compose.onNodeWithTag("vehicle-search").performTextReplacement(" g1 ")
            compose.onNodeWithTag("vehicle-search").performImeAction()
            awaitHistory()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("CR400BFA").fetchSemanticsNodes().size >= 3
            }
            fixture.capture("vehicle-query-by-train")
            compose.onNodeWithTag("vehicle-search").performTextReplacement("380B-3626")
            compose.onNodeWithTag("vehicle-search").performImeAction()
            awaitHistory()
            compose.onAllNodesWithText("km/h", substring = true).onFirst().assertIsDisplayed()
            compose.onAllNodesWithText("CRH380B-3626").onFirst().assertIsDisplayed()
            compose.waitUntil(60_000) {
                // Timetable bridge must enrich the full card, not leave every field unavailable.
                compose.onAllNodesWithText("km/h", substring = true).fetchSemanticsNodes().any {
                    !it.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                        .joinToString().contains(fixture.activity.getString(R.string.display_unavailable))
                }
            }
            fixture.capture("vehicle-query-by-vehicle")
            compose.onAllNodesWithTag("vehicle-number:CRH380B-3626").onFirst().performClick()
            val selected = compose.onNodeWithTag("vehicle-search").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            assertTrue(VehicleSearch.parse(selected)?.byTrain == true)
            awaitHistory()
            compose.onAllNodesWithTag("vehicle-number:CRH380B-3626").assertCountEquals(0)
            fixture.capture("vehicle-query-badge-link")
            compose.onNodeWithContentDescription(fixture.activity.getString(R.string.vehicle_query_clear))
                .performClick()
            compose.onNodeWithTag("vehicle-welcome").assertIsDisplayed()
            compose.onNodeWithTag("vehicle-history").assertDoesNotExist()
            compose.onNodeWithTag("vehicle-recent:380B-3626").assertIsDisplayed()
            compose.onNodeWithTag("vehicle-recent:G1").assertIsDisplayed()
            fixture.capture("vehicle-query-recent")
            compose.onNodeWithTag("vehicle-history-clear").performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithTag("vehicle-recent:G1").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("vehicle-history-empty").assertIsDisplayed()
            compose.onNodeWithTag("vehicle-search").performTextReplacement("../G1")
            compose.onNodeWithTag("vehicle-search").performImeAction()
            compose.onNodeWithText(fixture.activity.getString(R.string.vehicle_query_invalid)).assertIsDisplayed()
        } finally {
            fixture.close()
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, previous).commit()
            recent.edit().apply {
                if (savedHistory == null) remove("history") else putString("history", savedHistory)
            }.commit()
        }
    }

    private fun awaitHistory() {
        compose.waitUntil(35_000) {
            compose.onAllNodesWithTag("vehicle-history").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }
}
