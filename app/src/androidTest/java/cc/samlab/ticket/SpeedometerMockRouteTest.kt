package cc.samlab.ticket

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.StationArrivalMonitor
import cc.samlab.ticket.speedometer.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeedometerMockRouteTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val generated = mutableListOf<SpeedRecording>()

    @Test fun realGpsProviderStraightSignalGapAndPauseRoutes() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        onMain { SpeedometerStore.initialize(context) }
        assumeTrue(SpeedometerStore.current == null)
        val previous = SpeedometerStore.history
        val keep = InstrumentationRegistry.getArguments().getString("keepSpeedRoutes") == "true"
        val arrival = StationArrivalMonitor.get(context)
        onMain { arrival.stop() }
        try {
            SpeedometerMockGps(context).use { gps ->
                for (route in 0..2) runRoute(gps, route)
            }
            if (keep) {
                launch("/railfan/speedometer").use {
                    compose.onNode(hasScrollToIndexAction())
                        .performScrollToNode(hasTestTag("speed-record:${generated.last().id}"))
                    capture("mock-history")
                }
            }
        } finally {
            onMain { context.stopService(Intent(context, SpeedometerService::class.java)) }
            instrumentation.waitForIdleSync()
            onMain {
                SpeedometerStore.current = null
                SpeedometerStore.history = if (keep) generated.reversed() + previous else previous
                SpeedometerStore.checkpoint()
                arrival.restore()
            }
        }
    }

    private fun runRoute(gps: SpeedometerMockGps, route: Int) {
        val name = listOf("直线模拟", "断讯弯道模拟", "暂停续行模拟")[route]
        launch("/railfan/speedometer").use {
            onMain {
                context.startForegroundService(Intent(context, SpeedometerService::class.java)
                    .putExtra("train", "G900${route + 1}")
                    .putExtra("from", listOf("北京南", "上海虹桥", "广州")[route])
                    .putExtra("to", listOf("永定门外", "七宝", "三元里")[route])
                    .putExtra("vehicle", listOf("YW25G-123456", "CR400BF-Z-0524", "CR400AF-2251")[route]))
            }
            compose.waitUntil(10_000) { SpeedometerStore.current != null }
        }
        launch("/railfan/speedometer/live").use {
            val start = SystemClock.elapsedRealtime()
            var paused = false
            var resumed = false
            var lost = false
            var pausedAt = 0L
            var pausedDistance = 0.0
            var nextReport = 0
            while ((SpeedometerStore.current?.elapsed ?: 0) < 65_000) {
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                if (route == 2 && seconds >= 15 && !paused) {
                    command(SpeedometerService.PAUSE)
                    compose.waitUntil(5000) { SpeedometerStore.paused }
                    paused = true
                    pausedAt = SpeedometerStore.current!!.elapsed
                    pausedDistance = SpeedometerStore.current!!.meters
                    report("$name PAUSE active=$pausedAt")
                }
                if (route == 2 && seconds >= 23 && !resumed) {
                    assertEquals(pausedAt, SpeedometerStore.current!!.elapsed)
                    assertEquals(pausedDistance, SpeedometerStore.current!!.meters, 0.01)
                    command(SpeedometerService.PAUSE)
                    compose.waitUntil(5000) { !SpeedometerStore.paused }
                    resumed = true
                    report("$name RESUME")
                }
                if (route == 1 && seconds in 18.0..34.0) {
                    if (seconds >= 30 && !lost) {
                        compose.waitUntil(5000) { !SpeedometerStore.signal }
                        assertNull(SpeedometerStore.speed)
                        compose.onNodeWithText("信号中断").assertIsDisplayed()
                        capture("mock-signal-gap")
                        lost = true
                        report("$name SIGNAL LOST")
                    }
                } else {
                    val injectedAt = gps.inject(route, seconds)
                    if (!SpeedometerStore.paused) {
                        compose.waitUntil(5000) {
                            (SpeedometerStore.current?.points?.lastOrNull()?.time ?: 0) >= injectedAt
                        }
                        assertEquals(72.0, SpeedometerStore.speed!!, 0.1)
                    }
                }
                if (seconds.toInt() >= nextReport) {
                    report("$name active=${SpeedometerStore.current!!.elapsed} points=${SpeedometerStore.current!!.points.size}")
                    nextReport += 15
                }
                Thread.sleep(1000)
            }
            capture("mock-live-$route")
            command(SpeedometerService.STOP)
            compose.waitUntil(5000) { SpeedometerStore.current == null }
        }
        val record = SpeedometerStore.history.first()
        assertTrue(record.canSave)
        assertTrue(record.points.size >= 35)
        assertTrue(record.meters in 800.0..1700.0)
        if (route == 1) {
            assertTrue(lostGap(record))
        }
        if (route == 2) assertEquals(2, record.points.map { it.segment }.distinct().size)
        generated += record
        launch("/railfan/speedometer/track?id=${record.id}").use {
            compose.onNodeWithText("行程轨迹").assertIsDisplayed()
            Thread.sleep(3000)
            capture("mock-track-$route")
        }
        report("$name SAVED ${record.elapsed}ms ${record.meters}m points=${record.points.size} average=${record.average}")
    }

    private fun lostGap(record: SpeedRecording) =
        record.points.zipWithNext().any { (a, b) -> b.time - a.time > 12_000 && a.segment == b.segment }

    private fun command(action: String) = onMain { SpeedometerService.command(context, action) }
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun launch(route: String) = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java).putExtra(SpeedometerService.OPEN_RECORDING, route),
    ).also { scenario ->
        scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("important-notice-confirm").performClick()
        }
        compose.waitForIdle()
    }

    private fun report(message: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "speedometer-scenes").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
