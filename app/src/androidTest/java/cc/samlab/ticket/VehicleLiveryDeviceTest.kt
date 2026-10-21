package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleQueryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleLiveryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun specialLiveriesAndCr200ImagesLoadAndBadgesOpenServiceHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val history = target.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE)
        val saved = history.getString("history", null)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/railfan/vehicle")
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("vehicle-search").assertIsDisplayed() }.isSuccess
            }
            listOf("CR400BFZ0524", "CR400BFC5162", "CR200J1001", "CR200JC0501").forEach { number ->
                val vehicle = VehicleHistory(number, "", "")
                val image = runBlocking {
                    VehicleQueryRepository.image(target, vehicle.modelKey, number)
                }
                assertNotNull("Missing artwork for $number", image)
                compose.onNodeWithTag("vehicle-search").performTextReplacement(number)
                compose.onNodeWithTag("vehicle-search").performImeAction()
                awaitHistory()
                compose.onAllNodesWithTag("vehicle-number:${vehicle.displayVehicle}")
                    .onFirst().assertIsDisplayed()
                fixture.capture("vehicle-livery-badge-$number")
                compose.onAllNodesWithTag("vehicle-number:${vehicle.displayVehicle}")
                    .onFirst().performClick()
                awaitHistory()
                compose.onNodeWithTag("vehicle-history")
                    .performScrollToNode(hasText(vehicle.displayVehicle))
                compose.onAllNodesWithText(vehicle.displayVehicle).onFirst().assertIsDisplayed()
                fixture.capture("vehicle-livery-image-$number")
            }
        } finally {
            fixture.close()
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
}
