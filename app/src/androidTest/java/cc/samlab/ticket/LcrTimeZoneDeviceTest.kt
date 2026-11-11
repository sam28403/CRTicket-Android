package cc.samlab.ticket

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.trainquery.NativeTrainQueryController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LcrTimeZoneDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun showsOperatingCalendarWhenSelectedDateHasNoService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        WelcomeTestState(instrumentation.targetContext).use {
            val fixture = TabletPageFixture(compose)
            try {
                fixture.evaluate(
                    "window.crticketNavigate('/train?train=K11&date=2026-11-22&source=llt&dataset=lcr')",
                )
                lateinit var controller: NativeTrainQueryController
                instrumentation.runOnMainSync {
                    controller = fixture.activity.testController<NativeTrainQueryController>()
                }
                val tag = "train-operating-calendar"
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag(tag).assertIsDisplayed()
                assertEquals(0, controller.state.getJSONObject("result").getJSONArray("stops").length())
                val rows = controller.state.getJSONArray("operatingDays")
                val flags = (0 until rows.length()).associate {
                    val row = rows.getJSONArray(it)
                    row.getString(0) to row.getString(1)
                }
                assertEquals("1", flags["20261121"])
                assertEquals("0", flags["20261122"])
                assertEquals(null, flags["20261125"])
            } finally {
                fixture.close()
            }
        }
    }

    @Test fun switchesOfflineScheduleFromVientianeToBeijingTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        WelcomeTestState(instrumentation.targetContext).use {
            val fixture = TabletPageFixture(compose)
            try {
                fixture.evaluate(
                    "window.crticketNavigate('/train?train=C85&date=2026-11-11&source=llt&dataset=lcr')",
                )
                lateinit var controller: NativeTrainQueryController
                instrumentation.runOnMainSync {
                    controller = fixture.activity.testController<NativeTrainQueryController>()
                }
                val tag = "train-query-time-zone"
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("vientiane", controller.state.optString("selectedTimeZone"))
                assertEquals("18:35", controller.state.getJSONObject("result")
                    .getJSONArray("stops").getJSONObject(0).getString("departure"))
                compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
                compose.onNodeWithText("北京时间").performClick()
                compose.waitUntil(10_000) { controller.state.optString("selectedTimeZone") == "beijing" }
                assertEquals("19:35", controller.state.getJSONObject("result")
                    .getJSONArray("stops").getJSONObject(0).getString("departure"))
            } finally {
                fixture.close()
            }
        }
    }
}
