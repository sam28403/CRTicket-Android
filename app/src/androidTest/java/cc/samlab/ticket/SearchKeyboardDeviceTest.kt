package cc.samlab.ticket

import android.view.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchKeyboardDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun openingTrainAndHelpDismissesTheSearchKeyboard() {
        val fixture = TabletPageFixture(compose)
        fun awaitKeyboard(visible: Boolean) {
            compose.waitUntil(10_000) {
                var shown = false
                fixture.instrumentation.runOnMainSync {
                    shown = fixture.activity.window.decorView.rootWindowInsets
                        ?.isVisible(WindowInsets.Type.ime()) == true
                }
                shown == visible
            }
        }
        try {
            fixture.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            val results = compose.onNodeWithTag("global-search-results")
            field.performClick()
            field.performTextReplacement("G1")
            awaitKeyboard(true)
            compose.waitUntil(60_000) {
                runCatching {
                    results.performScrollToNode(hasTestTag("train-card:G1"))
                }.isSuccess
            }
            compose.onNodeWithTag("train-card:G1").performClick()
            compose.waitUntil(10_000) {
                fixture.evaluate("location.hash.startsWith('#/train?')") == "true"
            }
            awaitKeyboard(false)
            fixture.capture("search-train-keyboard-dismissed")
            fixture.navigate("/search")
            field.performClick()
            val title = fixture.activity.getString(R.string.help_general_title)
            field.performTextReplacement(title)
            awaitKeyboard(true)
            results.performScrollToNode(hasText(title) and hasClickAction() and !hasSetTextAction())
            compose.onAllNodes(hasText(title) and hasClickAction() and !hasSetTextAction())
                .onFirst().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("help-general").fetchSemanticsNodes().isNotEmpty()
            }
            awaitKeyboard(false)
            fixture.capture("search-help-keyboard-dismissed")
            for ((titleId, targetTag) in listOf(
                R.string.railfan_lines to "line-search",
                R.string.home_station to "station-query",
            )) {
                fixture.navigate("/search")
                field.performClick()
                val name = fixture.activity.getString(titleId)
                field.performTextReplacement(name)
                awaitKeyboard(true)
                val match = hasText(name) and hasClickAction() and !hasSetTextAction()
                results.performScrollToNode(match)
                compose.onAllNodes(match).onFirst().performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag(targetTag).fetchSemanticsNodes().isNotEmpty()
                }
                awaitKeyboard(false)
                fixture.capture("search-$targetTag-keyboard-dismissed")
            }
            for ((keyword, resultTag, targetTag) in listOf(
                Triple("沪昆", "search-line:沪昆高速线", "line-search"),
                Triple("北京南", "station-random-card:北京南", "station-query"),
            )) {
                fixture.navigate("/search")
                field.performClick()
                field.performTextReplacement(keyword)
                awaitKeyboard(true)
                compose.waitUntil(60_000) {
                    runCatching { results.performScrollToNode(hasTestTag(resultTag)) }.isSuccess
                }
                compose.onNodeWithTag(resultTag).performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag(targetTag).fetchSemanticsNodes().isNotEmpty()
                }
                awaitKeyboard(false)
                fixture.capture("search-prefilled-$targetTag-keyboard-dismissed")
            }

        } finally {
            fixture.capture("search-keyboard-final")
            fixture.close()
        }
    }
}
