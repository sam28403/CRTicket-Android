package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleQueryBackDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun backRestoresVehicleThenTrainThenSearchThenHome() {
        val fixture = TabletPageFixture(compose)
        fun keyword() = compose.onNodeWithTag("vehicle-search").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        fun search(value: String) {
            compose.onNodeWithTag("vehicle-search").performTextReplacement(value)
            compose.onNodeWithTag("vehicle-search").performImeAction()
        }
        fun backTo(value: String) {
            fixture.shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) { runCatching { keyword() == value }.getOrDefault(false) }
        }
        try {
            fixture.navigate("/")
            fixture.navigate("/railfan/vehicle")
            search("C3896")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("CRH2A-2001").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("CRH2A-2001").onFirst().performClick()
            compose.waitUntil(10_000) { keyword() == "CRH2A-2001" }
            search("2001")
            backTo("CRH2A-2001")
            backTo("C3896")
            compose.onAllNodesWithText("CRH2A-2001").onFirst().assertExists()
            backTo("")
            compose.onNodeWithTag("vehicle-welcome").assertIsDisplayed()
            fixture.shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("vehicle-search").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("true", fixture.evaluate("location.hash === '#/'"))
        } finally {
            fixture.close()
        }
    }
}
