package cc.samlab.ticket

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleSearch
import cc.samlab.ticket.ui.search.SearchVehicleAssignment
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SearchVehicleFailureTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun failedLookupHidesAllAssignmentContentAndKeepsOtherResults() {
        val response = CompletableDeferred<List<VehicleHistory>>()
        var failed = false
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/search")
            fixture.instrumentation.runOnMainSync {
                val view = fixture.activity.findViewById<ComposeView>(R.id.expressive_navigation_view)
                view.setContent {
                    MaterialTheme {
                        Column(Modifier.testTag("assignment-test-results")) {
                            Text("Other search results")
                            SearchVehicleAssignment(
                                query = VehicleSearch.forTrain("G1")!!,
                                active = true,
                                onNavigate = {},
                                onFailure = { failed = true },
                                search = { response.await() },
                            )
                        }
                    }
                }
            }
            compose.onNodeWithTag("global-vehicle-search").assertExists()
            response.completeExceptionally(IOException("Simulated lookup failure"))
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("global-vehicle-search").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("global-vehicle-latest").assertDoesNotExist()
            compose.onNodeWithTag("assignment-test-results").onChildren().assertCountEquals(1)
            compose.onNodeWithText("Other search results").assertExists()
            compose.runOnIdle { assertTrue(failed) }
        } finally {
            fixture.close()
        }
    }
}
