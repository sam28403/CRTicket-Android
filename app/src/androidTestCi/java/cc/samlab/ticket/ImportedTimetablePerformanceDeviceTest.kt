package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import cc.samlab.ticket.ui.settings.debugTickets

import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationTab
import cc.samlab.ticket.ui.trainlist.NativeTrainListController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportedTimetablePerformanceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun profileFullDatabaseImportAndQueriesInUnminifiedCi() {
        assertTrue(BuildConfig.CI_BUILD && BuildConfig.DEBUG)
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val source = File(context.getExternalFilesDir(null), "llt-performance/an.db")
        assertTrue("Push a complete an.db into the CI app's llt-performance directory", source.isFile)
        val settings = controller(context, "settings") as NativeSettingsController
        val list = controller(context, "trainList") as NativeTrainListController
        val station = controller(context, "stationBoard") as NativeStationBoardController
        val prefs = context.getSharedPreferences("developer-options", 0)
        val versions = context.getSharedPreferences("data-versions", 0)
        val oldVersion = versions.getString("llt", null)
        val originalNetwork = NetworkFeatures.isAllowed()
        val originalAuto = prefs.getBoolean("lltAutoUpdate", true)
        val originalVisibility = settings.developer.showNonRunningTrains
        val assets = fixture.instrumentation.context.assets
        val phase = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("phase", "baseline")
        val report = JSONObject().put("device", android.os.Build.MODEL)
            .put("buildType", BuildConfig.BUILD_TYPE).put("debuggable", BuildConfig.DEBUG)
            .put("archiveBytes", source.length()).put("phase", phase)
        val frames = FramePerformanceRecorder(context.window)
        fixture.instrumentation.runOnMainSync { frames.attach() }
        for (asset in listOf("non-running-store-fixture.js", "timetable-performance.js")) {
            fixture.evaluate(assets.open(asset).bufferedReader().use { it.readText() })
        }
        try {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, false)
                settings.developer.changeShowNonRunningTrains(true)
            }
            fixture.evaluate("window.dispatchEvent(new Event('crticket-network-changed')); " +
                "window.crticketTimetable.configure().then(() => window.nonRunningStore('save'))")
            awaitStore(fixture)
            fixture.evaluate("window.dispatchEvent(new Event('crticket-timetable-visibility-changed'))")
            fixture.navigate("/settings/developer/timetable")
            frames.reset()
            report.put("appPssBeforeKiB", Debug.getPss())
            fixture.evaluate("window.timetablePerfStart()")
            val importStart = SystemClock.elapsedRealtime()
            fixture.instrumentation.runOnMainSync { settings.timetable.importFile(Uri.fromFile(source)) }
            compose.waitUntil(60_000) { !settings.timetable.busy }
            report.put("importMs", SystemClock.elapsedRealtime() - importStart)
                .put("importJs", JSONObject(fixture.evaluate("window.timetablePerfStop()")))
                .put("importFrames", frames.summary()).put("appPssAfterKiB", Debug.getPss())
                .put("version", settings.timetable.version)
            assertTrue(settings.timetable.version.matches(Regex("\\d{8}")))

            frames.reset()
            val listStart = SystemClock.elapsedRealtime()
            fixture.evaluate("window.crticketNavigate('/list')")
            compose.waitUntil(30_000) { !list.loading && list.rows.size > 1000 }
            report.put("firstListMs", SystemClock.elapsedRealtime() - listStart)
                .put("trains", list.rows.size).put("listFrames", frames.summary())
            val warm = JSONArray()
            repeat(3) {
                val previousSnapshot = list.snapshotRevision
                val start = SystemClock.elapsedRealtime()
                fixture.instrumentation.runOnMainSync { list.request() }
                compose.waitUntil(30_000) { list.snapshotRevision > previousSnapshot && !list.loading }
                warm.put(SystemClock.elapsedRealtime() - start)
            }
            report.put("repeatListMs", warm)
            frames.reset()
            repeat(5) { compose.onNodeWithTag("train-list").performTouchInput { swipeUp() } }
            report.put("listScrollComposeFrames", frames.summary())
            val bounds = compose.onNodeWithTag("train-list").fetchSemanticsNode().boundsInWindow
            frames.reset()
            repeat(5) {
                injectTimedSwipe(fixture.instrumentation, bounds.center.x,
                    bounds.bottom - 80f, bounds.top + 80f)
                Thread.sleep(400)
            }
            Thread.sleep(700)
            report.put("listScrollFrames", frames.summary())

            val route = "/station?station=北京南"
            val board = station.stateFor(route)
            frames.reset()
            val stationStart = SystemClock.elapsedRealtime()
            fixture.evaluate("window.crticketNavigate('$route')")
            compose.waitUntil(30_000) { !board.loading && board.tabs[StationTab.ALL]?.isNotEmpty() == true }
            report.put("firstStationMs", SystemClock.elapsedRealtime() - stationStart)
                .put("stationTrains", board.tabs.getValue(StationTab.ALL).size)
                .put("stationFrames", frames.summary())
            fixture.evaluate("window.timetablePerfStart(); window.perfClearDone = false; " +
                "window.crticketStorage('perf-clear', 'clear').then(() => " +
                "window.crticketTimetable.snapshot()).then(() => window.perfClearDone = true)")
            val clearStart = SystemClock.elapsedRealtime()
            compose.waitUntil(30_000) { fixture.evaluate("window.perfClearDone") == "true" }
            report.put("clearAndReloadMs", SystemClock.elapsedRealtime() - clearStart)
                .put("clearJs", JSONObject(fixture.evaluate("window.timetablePerfStop()")))
            val folder = File(context.getExternalFilesDir(null), "logs/ci-timetable-performance").apply { mkdirs() }
            File(folder, "$phase.json").writeText(report.toString(2))
        } finally {
            fixture.evaluate("window.nonRunningStore('restore')")
            awaitStore(fixture)
            fixture.instrumentation.runOnMainSync {
                frames.close()
                settings.developer.changeShowNonRunningTrains(originalVisibility)
                NetworkFeatures.setEnabled(context, originalNetwork)
                prefs.edit().putBoolean("lltAutoUpdate", originalAuto).commit()
                val editor = versions.edit()
                if (oldVersion == null) editor.remove("llt") else editor.putString("llt", oldVersion)
                editor.commit()
            }
            fixture.close()
        }
    }

    private fun awaitStore(fixture: TabletPageFixture) {
        compose.waitUntil(20_000) { fixture.evaluate("window.nonRunningStoreResult") == "\"done\"" }
    }

    private fun controller(activity: MainActivity, name: String): Any? {
        val field = MainActivity::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }
        return (field.get(activity) as Lazy<*>).value
    }
}
