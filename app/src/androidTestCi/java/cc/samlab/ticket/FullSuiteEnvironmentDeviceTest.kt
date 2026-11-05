package cc.samlab.ticket

import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FullSuiteEnvironmentDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun manageTemporaryCurrentTimetable() {
        val phase = InstrumentationRegistry.getArguments().getString("suiteEnvironment")
        assumeTrue("Host-managed fixture", phase == "prepare" || phase == "restore")
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val settings = context.testController<NativeSettingsController>()
        val prefs = context.getSharedPreferences("developer-options", 0)
        val versions = context.getSharedPreferences("data-versions", 0)
        val folder = File(context.getExternalFilesDir(null), "logs/full-suite-environment").apply { mkdirs() }
        val backup = File(folder, "original.json")
        val script = fixture.instrumentation.context.assets.open("full-suite-store-fixture.js")
            .bufferedReader().use { it.readText() }
        fixture.evaluate(script)
        fun archive(action: String) {
            fixture.evaluate("window.fullSuiteArchive('$action')")
            compose.waitUntil(20_000) {
                fixture.evaluate("window.fullSuiteArchiveResult") != "\"pending\""
            }
            assertEquals("\"done\"", fixture.evaluate("window.fullSuiteArchiveResult"))
        }
        try {
            if (phase == "prepare") {
                assertFalse("Restore the previous suite fixture first", backup.exists())
                val original = JSONObject()
                    .put("network", NetworkFeatures.isAllowed())
                    .put("auto", prefs.getBoolean("lltAutoUpdate", true))
                    .put("visible", settings.developer.showNonRunningTrains)
                    .put("version", versions.getString("llt", null) ?: JSONObject.NULL)
                fixture.instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, false) }
                fixture.evaluate("window.crticketTimetable.configure().then(() => window.suitePaused = true)")
                compose.waitUntil(20_000) { fixture.evaluate("window.suitePaused") == "true" }
                archive("save")
                backup.writeText(original.toString())
                val source = File(context.getExternalFilesDir(null), "llt-performance/an.db")
                assertTrue(source.isFile)
                fixture.instrumentation.runOnMainSync { settings.timetable.importFile(Uri.fromFile(source)) }
                compose.waitUntil(60_000) { !settings.timetable.busy }
                assertEquals("20261108", settings.timetable.version)
                archive("cache")
                fixture.instrumentation.runOnMainSync {
                    settings.developer.changeShowNonRunningTrains(false)
                    NetworkFeatures.setEnabled(context, true)
                    settings.timetable.changeAutoUpdate(false)
                }
            } else {
                val original = JSONObject(backup.readText())
                fixture.instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, false) }
                fixture.evaluate("window.crticketTimetable.configure().then(() => window.suitePaused = true)")
                compose.waitUntil(20_000) { fixture.evaluate("window.suitePaused") == "true" }
                archive("restore")
                fixture.instrumentation.runOnMainSync {
                    NetworkFeatures.setEnabled(context, original.getBoolean("network"))
                    settings.developer.changeShowNonRunningTrains(original.getBoolean("visible"))
                    prefs.edit().putBoolean("lltAutoUpdate", original.getBoolean("auto")).commit()
                    versions.edit().apply {
                        if (original.isNull("version")) remove("llt")
                        else putString("llt", original.getString("version"))
                    }.commit()
                }
                backup.delete()
            }
        } finally { fixture.close() }
    }
}
