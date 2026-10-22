package cc.samlab.ticket

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.speedometer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeedometerDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun setupGuideLiveAndTrackScenes() {
        org.junit.Assume.assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
        instrumentation.runOnMainSync { SpeedometerStore.initialize(context) }
        org.junit.Assume.assumeTrue(SpeedometerStore.current == null)
        val previous = SpeedometerStore.current
        val history = SpeedometerStore.history
        try {
            launch("/railfan/speedometer").use {
                instrumentation.runOnMainSync { SpeedometerStore.history = emptyList() }
                compose.onNodeWithText("配置本次行程").assertIsDisplayed()
                compose.onNodeWithTag("speed-history-empty").assertIsDisplayed()
                compose.onNodeWithText("开始记录").performClick()
                compose.onNodeWithText("请填写起点站和终点站").assertExists()
                compose.onNodeWithTag("speed-train").performTextInput("g1")
                compose.onNodeWithTag("speed-train").assertTextContains("G1")
                compose.onNodeWithTag("speed-station-${R.string.ticket_from}").performTextInput("自定义起点")
                compose.onNodeWithTag("speed-station-${R.string.ticket_to}").performTextInput("自定义起点")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNodeWithTag("speed-vehicle").performTextInput("yw25g-123456")
                compose.onNodeWithTag("speed-vehicle").assertTextContains("YW25G-123456")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                val departure = compose.onNodeWithTag("speed-station-${R.string.ticket_from}")
                    .fetchSemanticsNode().boundsInRoot
                val arrival = compose.onNodeWithTag("speed-station-${R.string.ticket_to}")
                    .fetchSemanticsNode().boundsInRoot
                assertEquals(departure.top, arrival.top, 1f)
                assertTrue(departure.right <= arrival.left)
                capture("setup")
                compose.onNodeWithText("开始记录").performScrollTo().performClick()
                compose.onNodeWithText("将手机竖直立在车窗旁").assertExists()
                capture("guide")
            }
            instrumentation.runOnMainSync {
                SpeedometerStore.current = fixture()
                SpeedometerStore.speed = 286.0
                SpeedometerStore.signal = true
            }
            launch("/railfan/speedometer/live").use { scenario ->
                Thread.sleep(800)
                compose.onNodeWithText("286").assertIsDisplayed()
                compose.onNodeWithText("km/h").assertIsDisplayed()
                compose.onNodeWithText("暂停").assertIsDisplayed()
                compose.onNodeWithText("停止").assertIsDisplayed()
                compose.onAllNodes(
                    hasScrollAction() and hasAnyAncestor(hasTestTag("speed-live-content")),
                ).assertCountEquals(0)
                val beforeSwipe = compose.onNodeWithText("286").fetchSemanticsNode().boundsInRoot.top
                compose.onNodeWithTag("speed-live-content").performTouchInput {
                    swipe(
                        start = androidx.compose.ui.geometry.Offset(width / 2f, height * 0.65f),
                        end = androidx.compose.ui.geometry.Offset(width / 2f, height * 0.35f),
                        durationMillis = 300,
                    )
                }
                assertEquals(beforeSwipe, compose.onNodeWithText("286").fetchSemanticsNode().boundsInRoot.top, 1f)
                capture("live-black")
                var orientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                scenario.onActivity {
                    orientation = it.requestedOrientation
                    it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                try {
                    compose.waitUntil(10_000) {
                        runCatching {
                            val bounds = compose.onNodeWithTag("speed-live-content").fetchSemanticsNode().boundsInRoot
                            bounds.width > bounds.height
                        }.getOrDefault(false)
                    }
                    Thread.sleep(1200)
                    compose.waitForIdle()
                    compose.onNodeWithText("286").assertIsDisplayed()
                    compose.onNodeWithText("暂停").assertIsDisplayed()
                    compose.onNodeWithText("停止").assertIsDisplayed()
                    compose.onNodeWithText("01:01:01").assertIsDisplayed()
                    capture("live-landscape")
                } finally {
                    scenario.onActivity { it.requestedOrientation = orientation }
                }
                compose.waitUntil(10_000) {
                    runCatching {
                        val bounds = compose.onNodeWithTag("speed-live-content").fetchSemanticsNode().boundsInRoot
                        bounds.height > bounds.width
                    }.getOrDefault(false)
                }
                instrumentation.runOnMainSync {
                    SpeedometerStore.signal = false
                    SpeedometerStore.speed = null
                }
                compose.onNodeWithText("信号中断").assertIsDisplayed()
                capture("signal-lost")
            }
            instrumentation.runOnMainSync {
                SpeedometerStore.current = null
                SpeedometerStore.history = listOf(fixture())
            }
            launch("/railfan/speedometer").use { scenario ->
                compose.onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasTestTag("train-card:G1"))
                compose.onNodeWithTag("train-card:G1").assertExists()
                compose.onNodeWithTag("train-card:G1").performClick()
                compose.onNodeWithText("行程轨迹").assertIsDisplayed()
                Thread.sleep(3000)
                var mapState = ""
                scenario.onActivity { activity ->
                    val mapView = webViews(activity.window.decorView).first { it !== activity.webView }
                    mapView.evaluateJavascript("""
                        JSON.stringify({leaflet: typeof L, scripts: [...document.scripts].map(s => s.src),
                          paths: document.querySelectorAll('.leaflet-overlay-pane svg path').length,
                          map: document.getElementById('map')?.getBoundingClientRect().toJSON(),
                          panePosition: getComputedStyle(document.querySelector('.leaflet-pane')).position,
                          styles: [...document.styleSheets].map(s => s.href),
                          viewport: [innerWidth, innerHeight]})
                    """.trimIndent()) { mapState = it }
                }
                compose.waitUntil(5000) { mapState.isNotEmpty() }
                assertTrue("Map failed to draw track: $mapState", mapState.contains("leaflet\\\":\\\"object"))
                val state = org.json.JSONObject(org.json.JSONArray("[$mapState]").getString(0))
                assertTrue("Map contains no track: $mapState", state.getInt("paths") > 0)
                assertTrue("Map is not visible: $mapState", state.getJSONObject("map").getDouble("height") > 100)
                capture("track")
            }
        } finally {
            instrumentation.runOnMainSync {
                SpeedometerStore.current = previous
                SpeedometerStore.history = history
                SpeedometerStore.speed = null
                SpeedometerStore.signal = false
            }
        }
    }

    @Test fun swipeDeleteUndoRestoresVehicleAndTrack() {
        org.junit.Assume.assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
        instrumentation.runOnMainSync { SpeedometerStore.initialize(context) }
        org.junit.Assume.assumeTrue(SpeedometerStore.current == null)
        val previous = SpeedometerStore.history
        val record = fixture().copy(started = 2000, vehicle = "YW25G-123456")
        val older = fixture().copy(id = "speed-older", train = "K1", started = 1000)
        try {
            instrumentation.runOnMainSync { SpeedometerStore.history = listOf(record, older) }
            launch("/railfan/speedometer").use {
                val card = compose.onNodeWithTag("speed-record:${record.id}")
                compose.onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasTestTag("speed-record:${record.id}"))
                compose.onNodeWithTag("vehicle-number:${record.vehicle}").assertExists()
                card.performTouchInput { swipeLeft() }
                compose.onNodeWithText("已删除").assertIsDisplayed()
                assertEquals(listOf(older), SpeedometerStore.history)
                capture("delete-snackbar")
                compose.onNodeWithText("撤销").performClick()
                compose.waitUntil(5000) { SpeedometerStore.history.size == 2 }
                assertEquals(listOf(record, older), SpeedometerStore.history)
                capture("undo-restored")
                compose.onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasTestTag("speed-record:${record.id}"))
                card.assertIsDisplayed()
                compose.onNodeWithTag("vehicle-number:${record.vehicle}").assertExists()
                // Verify restored rows can be deleted again and do not re-delete on their own.
                compose.waitForIdle()
                assertEquals(record, SpeedometerStore.history.first())
                card.performTouchInput { swipeLeft() }
                compose.onNodeWithText("已删除").assertIsDisplayed()
                compose.waitUntil(5000) {
                    runCatching {
                        val json = org.json.JSONArray(File(context.filesDir, "speedometer.json").readText())
                        json.length() == 1 && json.getJSONObject(0).getString("id") == older.id
                    }.getOrDefault(false)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                SpeedometerStore.history = previous
                SpeedometerStore.checkpoint()
            }
        }
    }

    @Test fun homeEntryMatchesGpsHardware() {
        val enabled = RailfanPreferences.enabled(context)
        try {
            instrumentation.runOnMainSync { RailfanPreferences.setEnabled(context, true) }
            launch("/home").use {
                if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("important-notice-confirm").performClick()
                }
                compose.onNodeWithText(context.getString(R.string.railfan_vehicle)).performScrollTo().assertIsDisplayed()
                val hasGps = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                if (hasGps) {
                    compose.onNodeWithText("车速表").performScrollTo().assertIsDisplayed()
                } else {
                    compose.onNodeWithText("车速表").assertDoesNotExist()
                }
                capture("home-gps-$hasGps")
            }
        } finally {
            instrumentation.runOnMainSync { RailfanPreferences.setEnabled(context, enabled) }
        }
    }

    @Test fun shortRecordingIsDiscardedAndMinuteBoundaryIsSaved() {
        instrumentation.runOnMainSync { SpeedometerStore.initialize(context) }
        org.junit.Assume.assumeTrue(SpeedometerStore.current == null)
        val previous = SpeedometerStore.history
        try {
            instrumentation.runOnMainSync {
                SpeedometerStore.current = fixture().copy(id = "short", elapsed = 59_999)
                SpeedometerStore.finish()
                assertEquals(previous, SpeedometerStore.history)
                SpeedometerStore.current = fixture().copy(id = "minute", elapsed = 60_000)
                SpeedometerStore.finish()
                assertEquals("minute", SpeedometerStore.history.first().id)
            }
        } finally {
            instrumentation.runOnMainSync {
                SpeedometerStore.history = previous
                SpeedometerStore.checkpoint()
            }
        }
    }

    @Test fun servicePauseResumeAndStopPersist() {
        org.junit.Assume.assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        launch("/railfan/speedometer").use {
            val previous = SpeedometerStore.history
            org.junit.Assume.assumeTrue(SpeedometerStore.current == null)
            try {
                instrumentation.runOnMainSync {
                    context.startForegroundService(Intent(context, SpeedometerService::class.java)
                        .putExtra("train", "G1").putExtra("from", "测试起点").putExtra("to", "测试终点")
                        .putExtra("vehicle", "CR400AF-2251"))
                }
                compose.waitUntil(10_000) { (SpeedometerStore.current?.elapsed ?: 0) >= 1000 }
                instrumentation.runOnMainSync { SpeedometerService.command(context, SpeedometerService.PAUSE) }
                compose.waitUntil(5000) { SpeedometerStore.paused }
                val pausedAt = SpeedometerStore.current!!.elapsed
                Thread.sleep(1200)
                assertEquals(pausedAt, SpeedometerStore.current!!.elapsed)
                instrumentation.runOnMainSync { SpeedometerService.command(context, SpeedometerService.PAUSE) }
                compose.waitUntil(5000) { SpeedometerStore.current!!.elapsed > pausedAt }
                compose.waitUntil(80_000) { SpeedometerStore.current!!.elapsed >= 60_000 }
                instrumentation.runOnMainSync { SpeedometerService.command(context, SpeedometerService.STOP) }
                compose.waitUntil(5000) { SpeedometerStore.current == null }
                assertEquals("G1", SpeedometerStore.history.first().train)
                assertEquals("CR400AF-2251", SpeedometerStore.history.first().vehicle)
                assertTrue(SpeedometerStore.history.first().elapsed >= 60_000)
                val saved = SpeedometerStore.history.first()
                compose.waitUntil(5000) {
                    runCatching {
                        val json = org.json.JSONArray(File(context.filesDir, "speedometer.json").readText())
                            .getJSONObject(0)
                        json.getString("id") == saved.id && json.getLong("elapsed") == saved.elapsed &&
                            json.getString("vehicle") == saved.vehicle
                    }.getOrDefault(false)
                }
            } finally {
                instrumentation.runOnMainSync {
                    context.stopService(Intent(context, SpeedometerService::class.java))
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    SpeedometerStore.history = previous
                    SpeedometerStore.checkpoint()
                }
            }
        }
    }

    private fun launch(route: String): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(SpeedometerService.OPEN_RECORDING, route))
        scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitForIdle()
        compose.waitUntil(20_000) {
            runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
        if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("important-notice-confirm").performClick()
        }
        compose.waitForIdle()
        return scenario
    }

    private fun fixture() = SpeedRecording("speed-scene", "G1", "北京南", "上海虹桥", 0,
        elapsed = 3_661_000, meters = 269_000.0,
        points = listOf(SpeedPoint(39.864, 116.378, 1, 0),
            SpeedPoint(39.65, 116.5, 2, 0), SpeedPoint(39.4, 116.8, 3, 0)))

    private fun webViews(view: android.view.View): List<android.webkit.WebView> = when (view) {
        is android.webkit.WebView -> listOf(view)
        is android.view.ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val output = File(context.getExternalFilesDir(null), "speedometer-scenes").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()!!.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
