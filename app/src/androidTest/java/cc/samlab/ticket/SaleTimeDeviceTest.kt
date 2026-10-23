package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleTimeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun queryValidationCityToggleAndHistory() {
        val fixture = TabletPageFixture(compose)
        val preferences = fixture.activity.getSharedPreferences("sale-time-query", 0)
        val saved = preferences.getString("history", null)
        try {
            preferences.edit().remove("history").commit()
            fixture.evaluate("""
                window.addEventListener('crticket-visited-stations-request', event => {
                    if (event.detail?.saleCatalog) {
                        event.stopImmediatePropagation();
                        CRTicketAndroid.stationBoardResult(JSON.stringify({
                            kind: 'saleCatalog', stations: [],
                            names: [
                                {name:'北京南', abbr:'bjn', en:'beijingnan', city:'北京', bureau:'京局'},
                                {name:'北京西', abbr:'bjx', en:'beijingxi', city:'北京', bureau:'京局'},
                                {name:'北京', abbr:'bj', en:'beijing', city:'北京', bureau:'京局'}
                            ]
                        }));
                    }
                }, true);
            """.trimIndent())
            // Native destinations intentionally mirror the web home route.
            fixture.evaluate("window.crticketNavigate('/sale-time')")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("sale-query").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sale-same-city").assertIsOn()
            compose.onNodeWithTag("sale-query").performTextReplacement("不存在的车站")
            compose.onNodeWithTag("sale-search").assertIsNotEnabled()
            compose.onNodeWithTag("sale-query").performTextReplacement("北京南")
            compose.onAllNodes(hasText("北京南") and hasClickAction()).onLast().performClick()
            compose.onNodeWithTag("sale-search").assertIsEnabled().performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("sale-card:北京").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sale-results").performScrollToNode(hasTestTag("sale-card:北京西"))
            compose.onNodeWithTag("sale-card:北京西").assertExists()
            fixture.capture("sale-time-city")
            compose.onNodeWithTag("sale-same-city").performClick().assertIsOff()
            compose.onNodeWithTag("sale-card:北京南").assertExists()
            compose.onNodeWithTag("sale-card:北京西").assertDoesNotExist()
            fixture.capture("sale-time-exact")
            compose.onNodeWithText("清空", substring = false).performClick()
            org.junit.Assert.assertNull(preferences.getString("history", null))
        } catch (failure: Throwable) {
            fixture.capture("sale-time-failure")
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("SaleTimeTest") }
            throw failure
        } finally {
            preferences.edit().apply {
                if (saved == null) remove("history") else putString("history", saved)
            }.commit()
            fixture.close()
        }
    }
}
