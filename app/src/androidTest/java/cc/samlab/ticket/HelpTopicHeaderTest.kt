package cc.samlab.ticket

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.help.helpTopics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpTopicHeaderTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun allTopicTitlesAreFullyVisible() {
        val fixture = TabletPageFixture(compose)
        try {
            helpTopics.forEach { topic ->
                fixture.navigate(topic.route)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("help-topic-title")
                        .fetchSemanticsNodes().size == 1
                }
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("help-topic-title").performSemanticsAction(
                    SemanticsActions.GetTextLayoutResult,
                ) { action -> action(layouts) }
                assertTrue("Missing title layout", layouts.isNotEmpty())
                layouts.forEach { layout ->
                    assertFalse("Clipped title: ${layout.layoutInput.text}", layout.hasVisualOverflow)
                }
                if (topic.id == "monitor") fixture.capture("help-monitor-title")
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun longHelpContentScrollsByTouchToTheEnd() {
        val fixture = TabletPageFixture(compose)
        try {
            helpTopics.forEach { topic ->
                fixture.navigate(topic.route)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("help-content").fetchSemanticsNodes().size == 1
                }
                val panel = compose.onNodeWithTag("help-content")
                val range = panel.fetchSemanticsNode().config[
                    SemanticsProperties.VerticalScrollAxisRange
                ]
                if (range.maxValue() > 0f) {
                    val before = range.value()
                    panel.performTouchInput { swipeUp() }
                    compose.waitForIdle()
                    assertTrue("Touch scroll failed: ${topic.id}", range.value() > before)
                }
                compose.onNodeWithTag("help-content-end").performScrollTo().assertIsDisplayed()
            }
            fixture.capture("help-content-scrolled")
        } finally {
            fixture.close()
        }
    }
}
