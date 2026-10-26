package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DelayCacheDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var welcome: WelcomeTestState

    @Before fun prepareStartup() {
        welcome = WelcomeTestState(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After fun restoreStartup() = welcome.close()

    @Test fun trainRefreshFailuresKeepCachedTimesAndEnabledSwitch() {
        val fixture = prepare()
        try {
            navigate(fixture, "/train?train=G1&date=${today()}")
            waitFor { compose.onNodeWithTag("train-query-stop-1").assert(hasText("20:00 (+7')")) }
            compose.onNodeWithTag("train-query-delay").assertIsOn()
            for (mode in listOf("failure", "empty", "partial")) {
                val before = fixture.evaluate("window.delayCacheFixture.trainRequests").toInt()
                val completed = fixture.evaluate("window.delayCacheFixture.completed").toInt()
                fixture.evaluate("window.delayCacheFixture.mode = '$mode'")
                if (mode == "empty") {
                    compose.onNodeWithTag("train-query-content").performScrollToIndex(0)
                    compose.onNodeWithTag("train-query-content").performTouchInput {
                        swipeDown(startY = height * 0.2f, endY = height * 0.9f, durationMillis = 800)
                    }
                } else {
                    compose.onNodeWithTag("train-query-refresh").assertIsEnabled().performClick()
                }
                compose.waitUntil(10_000) {
                    fixture.evaluate("window.delayCacheFixture.trainRequests").toInt() > before &&
                        fixture.evaluate("window.delayCacheFixture.completed").toInt() > completed
                }
                val arrival = if (mode == "partial") "19:53" else "20:00 (+7')"
                waitFor {
                    compose.onNodeWithTag("train-query-stop-1")
                        .assert(hasText(arrival)).assert(hasText("20:05 (+9')"))
                }
                compose.onNodeWithTag("train-query-delay").assertIsOn()
                fixture.capture("delay-cache-train-$mode")
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun refreshButtonOnlyAppearsOnlineAndRefreshesTheDisplayedTrain() {
        val fixture = prepare()
        try {
            navigate(fixture, "/train?train=G1&date=${today()}")
            waitFor { compose.onNodeWithTag("train-query-stop-1").assert(hasText("20:00 (+7')")) }
            waitFor { compose.onNodeWithTag("train-query-refresh").assertIsEnabled() }
            compose.onNodeWithTag("train-query-code").performTextReplacement("G2")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            val before = fixture.evaluate("window.delayCacheFixture.trainRequests").toInt()
            val timetable = fixture.evaluate("window.delayCacheFixture.timetableRequests").toInt()
            fixture.evaluate("window.delayCacheFixture.responseDelay = 1500")
            compose.onNodeWithTag("train-query-refresh").performClick()
            waitFor { compose.onNodeWithTag("train-query-refresh").assertIsNotEnabled() }
            waitFor { compose.onNodeWithTag("train-query-refresh").assertIsEnabled() }
            assertEquals(before + 1, fixture.evaluate("window.delayCacheFixture.trainRequests").toInt())
            assertEquals(timetable, fixture.evaluate("window.delayCacheFixture.timetableRequests").toInt())
            assertEquals("\"G1\"", fixture.evaluate("window.delayCacheFixture.lastTrain"))
            fixture.capture("train-refresh-online")

            navigate(fixture, "/train?train=G1&date=${today()}&source=llt")
            waitFor { compose.onNodeWithTag("train-query-refresh").assertDoesNotExist() }
            compose.onNodeWithTag("train-query-delay").assertDoesNotExist()
            fixture.capture("train-refresh-offline")
        } finally {
            fixture.close()
        }
    }

    @Test fun stationRefreshFailuresAndMissingFieldsKeepCachedTimes() {
        val fixture = prepare()
        try {
            navigate(fixture, "/station?station=嘉兴")
            waitFor { compose.onNodeWithText("20:00 (+7')").assertIsDisplayed() }
            for (mode in listOf("failure", "empty", "partial", "timetableFailure")) {
                fixture.evaluate("window.delayCacheFixture.mode = '$mode'")
                val before = fixture.evaluate("window.delayCacheFixture.timetableRequests").toInt()
                val completed = fixture.evaluate("window.delayCacheFixture.completed").toInt()
                compose.onNodeWithTag("station-refresh").performClick()
                compose.waitUntil(10_000) {
                    fixture.evaluate("window.delayCacheFixture.timetableRequests").toInt() > before &&
                        (mode == "timetableFailure" ||
                            fixture.evaluate("window.delayCacheFixture.completed").toInt() > completed)
                }
                val arrival = if (mode in listOf("partial", "timetableFailure")) "19:53" else "20:00 (+7')"
                waitFor {
                    compose.onNodeWithText(arrival).assertIsDisplayed()
                    compose.onNodeWithText("20:05 (+9')").assertIsDisplayed()
                }
                fixture.capture("delay-cache-station-$mode")
            }
            compose.onNodeWithTag("station-tab-departure").performClick()
            waitFor {
                compose.onNodeWithText("20:05", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithText("(+9')", useUnmergedTree = true).assertIsDisplayed()
            }
            fixture.capture("delay-cache-station-departure")
        } finally {
            fixture.close()
        }
    }

    private fun prepare(): TabletPageFixture {
        val fixture = TabletPageFixture(compose)
        val assets = fixture.instrumentation.targetContext.assets
        val apiFile = assets.list("web/assets")!!.first { name ->
            name.startsWith("api-") && assets.open("web/assets/$name")
                .bufferedReader().use { it.readText().contains("withCredentials") }
        }
        val script = fixture.instrumentation.context.assets.open("delay-cache-fixture.js")
            .bufferedReader().use { it.readText() }
        fixture.evaluate(script + "\ninstallDelayCacheFixture(${JSONObject.quote(apiFile)})" +
            ".catch(error => window.delayCacheFixtureError = String(error))")
        compose.waitUntil(10_000) {
            fixture.evaluate("Boolean(window.delayCacheFixture?.ready || window.delayCacheFixtureError)") == "true"
        }
        assertEquals(fixture.evaluate("window.delayCacheFixtureError"), "true",
            fixture.evaluate("Boolean(window.delayCacheFixture?.ready)"))
        return fixture
    }

    private fun waitFor(assertion: () -> Unit) {
        compose.waitUntil(15_000) { runCatching(assertion).isSuccess }
        compose.waitForIdle()
    }

    private fun navigate(fixture: TabletPageFixture, location: String) {
        fixture.evaluate("window.crticketNavigate(${JSONObject.quote(location)})")
        val path = JSONObject.quote("#${location.substringBefore('?')}")
        compose.waitUntil(10_000) {
            fixture.evaluate("location.hash.split('?')[0] === $path") == "true"
        }
        compose.waitForIdle()
    }

    private fun today() = LocalDate.now(ZoneId.of("Asia/Shanghai"))
}
