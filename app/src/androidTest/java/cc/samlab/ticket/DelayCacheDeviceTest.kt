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

    @Test fun trainPdfAlwaysUsesScheduledTimes() {
        val fixture = prepare()
        val automation = fixture.instrumentation.uiAutomation
        fun pickerOpen() = automation.rootInActiveWindow?.packageName
            ?.toString()?.endsWith("documentsui") == true
        fun exportPixels(name: String): android.graphics.Bitmap {
            compose.onNodeWithTag("train-query-content")
                .performScrollToNode(hasTestTag("train-query-export"))
            compose.onNodeWithTag("train-query-export").assertIsEnabled().performClick()
            compose.waitUntil(30_000) { pickerOpen() }
            val file = fixture.activity.cacheDir.listFiles().orEmpty()
                .filter { it.name.startsWith("export-") }.maxBy { it.lastModified() }
            val folder = java.io.File(fixture.activity.getExternalFilesDir(null), "train-pdf")
                .apply { mkdirs() }
            file.copyTo(java.io.File(folder, "$name.pdf"), overwrite = true)
            val bitmap = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        android.graphics.Bitmap.createBitmap(
                            page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888,
                        ).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null,
                                android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitUntil(10_000) { fixture.activity.hasWindowFocus() }
            waitFor { compose.onNodeWithTag("train-query-export").assertIsEnabled() }
            return bitmap
        }
        try {
            navigate(fixture, "/train?train=G1&date=${today()}")
            waitFor { compose.onNodeWithTag("train-query-stop-1").assert(hasText("20:00 (+7')")) }
            fixture.capture("train-pdf-delay-enabled")
            val delayed = exportPixels("delay-enabled")
            compose.onNodeWithTag("train-query-content").performScrollToIndex(0)
            compose.onNodeWithTag("train-query-delay").performClick()
            waitFor { compose.onNodeWithTag("train-query-stop-1").assert(hasText("19:53")) }
            val scheduled = exportPixels("scheduled")
            fun pixels(bitmap: android.graphics.Bitmap) = IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
            org.junit.Assert.assertArrayEquals("PDF must retain scheduled times and stay",
                pixels(scheduled), pixels(delayed))
            compose.onNodeWithTag("train-query-content").performScrollToIndex(0)
            compose.onNodeWithTag("train-query-refresh").performClick()
            waitFor { compose.onNodeWithTag("train-query-stop-1").assert(hasText("20:00 (+7')")) }
            val refreshed = exportPixels("refreshed")
            org.junit.Assert.assertArrayEquals("Refresh must not alter PDF",
                pixels(scheduled), pixels(refreshed))
            listOf(delayed, scheduled, refreshed).forEach { it.recycle() }
        } finally {
            if (pickerOpen()) automation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            )
            fixture.close()
        }
    }

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
        val pageTag = when (location.substringBefore('?')) {
            "/train" -> "train-query-content"
            "/station" -> "station-refresh"
            else -> error("Unsupported delay-cache route: $location")
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(pageTag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun today() = LocalDate.now(ZoneId.of("Asia/Shanghai"))
}
