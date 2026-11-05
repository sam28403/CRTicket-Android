package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import cc.samlab.ticket.ui.settings.debugTickets

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugTicketsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun generationPageShowsEmptyResultsAndSavedHistory() {
        val fixture = TabletPageFixture(compose)
        val previous = fixture.evaluate("localStorage.getItem('crticket-device-tickets-v1')")
        val originalNetwork = cc.samlab.ticket.network.NetworkFeatures.isAllowed()
        try {
            fixture.instrumentation.runOnMainSync {
                cc.samlab.ticket.network.NetworkFeatures.setEnabled(fixture.activity, true)
            }
            fixture.evaluate("localStorage.setItem('crticket-device-tickets-v1', '[]')")
            fixture.navigate("/settings/developer")
            compose.onNode(hasText(fixture.activity.getString(R.string.debug_tickets_title))
                and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithTag("debug-tickets-content").assertIsDisplayed()
            compose.onNodeWithTag("debug-tickets-empty").assertIsDisplayed()
            val date = compose.onNodeWithTag("debug-tickets-date").fetchSemanticsNode().boundsInRoot
            val count = compose.onNodeWithTag("debug-tickets-count").fetchSemanticsNode().boundsInRoot
            assertTrue(date.left < count.left)
            assertTrue(kotlin.math.abs(date.top - count.top) < 4f)
            fixture.capture("debug-tickets-empty")
            compose.onNodeWithTag("debug-tickets-date").performClick()
            compose.onNode(
                hasText(fixture.activity.getString(R.string.action_cancel)) and
                    hasAnyAncestor(hasTestTag("debug-tickets-date-dialog")),
            ).performClick()
            fixture.evaluate("""
                window.originalDebugGenerate = window.crticketDebugGenerate;
                window.crticketDebugGenerate = (operation, date, count) => {
                    const tickets = Array.from({length:count}, (_, index) => ({
                        id: 'debug-device-fixture-' + index, user_id:'device',
                        ticket_number:'A12345678' + index, train_no:'G1',
                        departure_station:'北京南', arrival_station:'上海虹桥',
                        travel_date:date.slice(0,4)+'年'+date.slice(5,7)+'月'+date.slice(8,10)+'日',
                        departure_time:'09:00', price:553, seat_type:'二等座',
                        seat_no:'01车01A号', distance:1318, use_credit:0,
                        has_conditioner:0, sell_place:'网', gate_info:'',
                        message:'', theme:'EMU_Green.jpg'
                    }));
                    const old = JSON.parse(localStorage.getItem('crticket-device-tickets-v1') || '[]');
                    localStorage.setItem('crticket-device-tickets-v1', JSON.stringify([...old, ...tickets]));
                    window.crticketDebugResult = {operation, success:true, tickets};
                }; true
            """.trimIndent())
            compose.onNodeWithTag("debug-tickets-count").performTextReplacement("2")
            compose.onNodeWithTag("debug-tickets-generate").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-record-debug-device-fixture-0")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("debug-tickets-empty").assertDoesNotExist()
            fixture.capture("debug-tickets-results")
            pressBack()
            compose.onNodeWithTag("settings-developer").assertIsDisplayed()
            fixture.navigate("/history")
            compose.onNodeWithTag("history-list").performScrollToNode(
                hasTestTag("history-record-debug-device-fixture-0"),
            )
            compose.onNodeWithTag("history-record-debug-device-fixture-0").assertIsDisplayed()
            fixture.navigate("/settings/developer/tickets")
            fixture.evaluate("""
                window.crticketDebugGenerate = operation => {
                    window.crticketDebugResult = {operation, success:false};
                }; true
            """.trimIndent())
            compose.onNodeWithTag("debug-tickets-generate").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("debug-tickets-empty").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasTestTag("debug-tickets-generate") and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            fixture.evaluate("""
                if ($previous === null) localStorage.removeItem('crticket-device-tickets-v1');
                else localStorage.setItem('crticket-device-tickets-v1', $previous);
                if (window.originalDebugGenerate) window.crticketDebugGenerate = window.originalDebugGenerate;
                true
            """.trimIndent())
            fixture.instrumentation.runOnMainSync {
                cc.samlab.ticket.network.NetworkFeatures.setEnabled(fixture.activity, originalNetwork)
            }
            fixture.close()
        }
    }
}
