package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingQueryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun queryInputsAreEmptyOnNewEntry() {
        val fixture = TabletPageFixture(compose)
        try {
            listOf(
                Triple("/station", "station-query", "北京南"),
                Triple("/railfan/routing", "routing-search", "G1"),
            ).forEach { (route, tag, input) ->
                fixture.navigate(route)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag(tag).performTextReplacement(input)
                fixture.navigate("/")
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
                }
                fixture.navigate(route)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag(tag).assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
                )
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun routingCardsHighlightAndBlockSpecialServices() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.evaluate("""
                const originalDispatch = window.dispatchEvent;
                window.dispatchEvent = function(event) {
                    if (event.type !== 'crticket-routing-request') {
                        return originalDispatch.call(this, event);
                    }
                    const {requestId, train, date} = event.detail;
                    const rows = ['G1', '0D55', 'DJ1234'].map(code => ({
                        train: code, from: '北京南', to: '上海虹桥',
                        duration: '04:54', stationCount: 7, mileage: 1318,
                        detailTrain: code, highlighted: code === train, clickable: code === 'G1'
                    }));
                    window.CRTicketAndroid.trainListResult(JSON.stringify({
                        kind: 'routing', requestId,
                        result: {train, date, version: '20261024',
                            groups: train === 'G1' ? [{id: 696, model: 'CR400BF-S 型重联', rows}] : []}
                    }));
                    return true;
                };
            """.trimIndent())
            fixture.navigate("/railfan/routing")
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onAllNodesWithTag("routing-search").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("important-notice-confirm").performClick()
            }
            compose.onNodeWithTag("routing-empty").assertIsDisplayed()
            fixture.capture("routing-before-search")
            compose.onNodeWithTag("routing-search").performTextInput("G99999")
            compose.onNodeWithTag("routing-search").performImeAction()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("routing-empty").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("routing-empty").assertIsDisplayed()
            fixture.capture("routing-no-results")
            compose.onNodeWithTag("routing-search").performTextClearance()
            compose.onNodeWithTag("routing-search").performTextInput("g1")
            compose.onNodeWithTag("routing-search").performImeAction()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("routing-results").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText("G1") and isSelected()).assertIsSelected().assertIsEnabled()
            fixture.capture("routing-special-services")
            compose.onNodeWithTag("routing-results").performScrollToNode(hasText("0D55"))
            compose.onNodeWithTag("train-card:0D55", useUnmergedTree = true).assertIsNotEnabled()
            compose.onNodeWithTag("routing-results").performScrollToNode(hasText("DJ1234"))
            compose.onNodeWithTag("train-card:DJ1234", useUnmergedTree = true).assertIsNotEnabled()
            fixture.capture("routing-special-services")
            compose.onNodeWithTag("routing-results").performScrollToNode(hasText("G1"))
            compose.onNode(hasText("G1") and isSelected()).performClick()
            compose.waitUntil(10_000) {
                fixture.evaluate("location.hash.includes('/train?')") == "true"
            }
            assertTrue(fixture.evaluate("location.hash.includes('train=G1')") == "true")
        } finally {
            fixture.close()
        }
    }
}
