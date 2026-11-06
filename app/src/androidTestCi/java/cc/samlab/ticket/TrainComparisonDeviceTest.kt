package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainComparisonDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun searchesOldAndNewNumbersAndKeepsListLazy() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/train-comparison")
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("train-comparison-search")
                    .fetchSemanticsNodes().any {
                        !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                    }
            }
            val field = compose.onNodeWithTag("train-comparison-query")
            field.performTextReplacement("K3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:V3").assertIsDisplayed().assertIsSelected()
            compose.onNode(
                hasText("K3") and hasAnyAncestor(hasTestTag("train-card:V3")),
                useUnmergedTree = true,
            )
                .assertIsDisplayed()
            fixture.capture("train-comparison-old-query")
            field.performTextReplacement("Z3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:Z3").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithTag("train-card:V3").assertDoesNotExist()
            field.performTextReplacement("V3")
            compose.onNodeWithTag("train-comparison-search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("train-card:V3").assertIsDisplayed().assertIsSelected()
            field.performTextReplacement("G12345")
            field.assertTextEquals("V3")
            fixture.capture("train-comparison-new-query")
        } finally {
            fixture.close()
        }
    }
}
