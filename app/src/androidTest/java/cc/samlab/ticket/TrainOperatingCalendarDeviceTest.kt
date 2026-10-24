package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.trainquery.*
import cc.samlab.ticket.ui.welcome.WelcomeState
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrainOperatingCalendarDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test fun colorsMonthNavigationAndHiddenEmptyCalendar() = withActivity { activity ->
        val sample = trainOperatingDays(listOf(
            "20261230" to "1", "20261231" to "0", "20270101" to "1",
        ))
        val state = mutableStateOf(OperatingCalendarState(sample, loading = false))
        val dark = mutableStateOf(false)
        instrumentation.runOnMainSync {
            activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                MaterialExpressiveTheme(colorScheme = homeColorScheme(activity, dark.value, false)) {
                    Box(Modifier.width(320.dp)) {
                        TrainOperatingCalendar(state.value, "2026-12-31", dark.value)
                    }
                }
            }
        }
        compose.onNodeWithTag("train-calendar-day-2026-12-30").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.train_calendar_running)),
        )
        compose.onNodeWithTag("train-calendar-day-2026-12-31").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.train_calendar_stopped)),
        )
        compose.onNodeWithTag("train-calendar-day-2026-12-29").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.train_calendar_unknown)),
        )
        capture("calendar-light")
        compose.onNodeWithTag("train-calendar-previous").assertIsNotEnabled()
        compose.onNodeWithTag("train-calendar-next").performClick()
        compose.onNodeWithTag("train-calendar-day-2027-01-01").assertIsDisplayed()
        compose.onNodeWithTag("train-calendar-next").assertIsNotEnabled()
        compose.onNodeWithTag("train-calendar-previous").performClick()
        compose.runOnIdle { dark.value = true }
        capture("calendar-dark")
        compose.runOnIdle { state.value = OperatingCalendarState(loading = false) }
        compose.onNodeWithTag("train-operating-calendar").assertDoesNotExist()
        compose.runOnIdle { state.value = OperatingCalendarState(trainOperatingDays(emptyList()), false) }
        compose.onNodeWithTag("train-operating-calendar").assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test fun officialCalendarAppearsBetweenExportAndMap() {
        val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val official = runBlocking { TrainOperatingDaysRepository.query("G1", date) }
        assertTrue(official.dates.containsKey(date))
        withActivity { activity ->
            lateinit var web: WebView
            lateinit var controller: NativeTrainQueryController
            instrumentation.runOnMainSync {
                web = WebView(activity)
                controller = NativeTrainQueryController(activity, web)
            }
            try {
                val result = currentTrainQueryFixture().put("date", date.toString())
                instrumentation.runOnMainSync {
                    controller.receive(JSONObject()
                        .put("location", "/train")
                        .put("result", result)
                        .put("route", JSONObject().put("points", org.json.JSONArray()))
                        .toString())
                    activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                        ExpressiveTrainQueryScreen(controller, false, false, {}, {})
                    }
                }
                compose.onNodeWithTag("train-query-content")
                    .performScrollToNode(hasTestTag("train-query-export"))
                compose.onNodeWithTag("train-operating-calendar").assertIsDisplayed()
                capture("calendar-result")
                val export = compose.onNodeWithTag("train-query-export").fetchSemanticsNode().boundsInRoot
                val calendar = compose.onNodeWithTag("train-operating-calendar").fetchSemanticsNode().boundsInRoot
                assertTrue(export.bottom <= calendar.top)
                compose.onNodeWithTag("train-query-content")
                    .performScrollToNode(hasTestTag("train-operating-calendar"))
                val month = YearMonth.from(date)
                if (activity.resources.configuration.screenWidthDp >= 600) {
                    val first = compose.onNodeWithTag("train-calendar-month-$month")
                        .fetchSemanticsNode().boundsInRoot
                    val second = compose.onNodeWithTag("train-calendar-month-${month.plusMonths(1)}")
                        .fetchSemanticsNode().boundsInRoot
                    assertEquals(first.top, second.top, 1f)
                    assertTrue(second.left > first.right)
                    compose.onNodeWithTag("train-calendar-next").performClick()
                    compose.onNodeWithTag("train-calendar-month-${month.plusMonths(2)}")
                        .assertIsDisplayed()
                    compose.onNodeWithTag("train-calendar-previous").performClick()
                } else {
                    compose.onNodeWithTag("train-calendar-month-${month.plusMonths(1)}")
                        .assertDoesNotExist()
                }
                capture("calendar-result-full")
            } finally {
                instrumentation.runOnMainSync {
                    controller.close()
                    web.destroy()
                }
            }
        }
    }

    private fun withActivity(block: (MainActivity) -> Unit) {
        val welcome = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
        val terms = welcome.getInt("terms", 0)
        val version = welcome.getString("version", null)
        WelcomeState.complete(context)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity {
                    activity = it
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                }
                instrumentation.runOnMainSync {
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                block(activity)
            }
        } finally {
            welcome.edit().putInt("terms", terms).putString("version", version).commit()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(700)
        val directory = File(context.getExternalFilesDir(null), "operating-calendar-verification")
        directory.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
