package cc.samlab.ticket

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.settings.timetable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TimetableImportDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun importDatabasePreservesVersionAcrossCleaningAndRestart() {
        var fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val prefs = context.getSharedPreferences("developer-options", 0)
        val versions = context.getSharedPreferences("data-versions", 0)
        val originalVersion = versions.getString("llt", null)
        val originalNetwork = NetworkFeatures.isAllowed()
        val originalAuto = prefs.getBoolean("lltAutoUpdate", true)
        val assets = fixture.instrumentation.context.assets
        val script = assets.open("non-running-store-fixture.js").bufferedReader().use { it.readText() }
        val source = File(context.cacheDir, "import-test.db")
        assets.open("non-running-trains.db").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        var controller = controller(fixture.activity)
        try {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, true)
                controller.timetable.changeAutoUpdate(false)
            }
            fixture.evaluate("window.crticketTimetable.configure().then(() => window.importPaused = true)")
            compose.waitUntil(20_000) { fixture.evaluate("window.importPaused") == "true" }
            fixture.evaluate(script)
            store(fixture, "persist")
            fixture.instrumentation.runOnMainSync { controller.timetable.changeAutoUpdate(true) }
            fixture.navigate("/settings/developer")
            compose.onNodeWithText(context.getString(R.string.llt_import_title)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.llt_auto_update)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.llt_import_title)).performScrollTo().performClick()
            compose.onNodeWithTag("llt-import-button").assertIsEnabled()
            fixture.instrumentation.runOnMainSync { controller.timetable.importFile(Uri.fromFile(source)) }
            compose.waitUntil(30_000) { !controller.timetable.busy }
            assertEquals("20000101", controller.timetable.version)
            compose.onNodeWithText("20000101").assertIsDisplayed()
            val invalid = File(context.cacheDir, "invalid-import.db").apply { writeText("invalid") }
            try {
                fixture.instrumentation.runOnMainSync { controller.timetable.importFile(Uri.fromFile(invalid)) }
                compose.waitUntil(30_000) { !controller.timetable.busy }
                assertEquals("20000101", controller.timetable.version)
            } finally {
                invalid.delete()
            }
            fixture.navigate("/about")
            compose.onNodeWithText(context.getString(R.string.about_llt_version, "20000101")).assertIsDisplayed()
            fixture.evaluate("window.crticketStorage('timetable-test', 'clear').then(() => window.lltCleanDone = true)")
            compose.waitUntil(20_000) { fixture.evaluate("window.lltCleanDone") == "true" }
            fixture.close()
            fixture = TabletPageFixture(compose)
            controller = controller(fixture.activity)
            fixture.navigate("/settings/developer/timetable")
            fixture.evaluate("window.crticketTimetable.snapshot().then(v => window.reopenedLltVersion = v)")
            compose.waitUntil(20_000) { fixture.evaluate("window.reopenedLltVersion") == "\"20000101\"" }
            compose.onNodeWithText("20000101").assertIsDisplayed()
        } finally {
            if (fixture.activity.isDestroyed) fixture = TabletPageFixture(compose)
            fixture.evaluate(script)
            store(fixture, "recover")
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(fixture.activity, originalNetwork)
                prefs.edit().putBoolean("lltAutoUpdate", originalAuto).commit()
                versions.edit().apply {
                    if (originalVersion == null) remove("llt") else putString("llt", originalVersion)
                }.commit()
            }
            source.delete()
            fixture.close()
        }
    }

    private fun store(fixture: TabletPageFixture, action: String) {
        fixture.evaluate("window.nonRunningStore('$action')")
        compose.waitUntil(20_000) { fixture.evaluate("window.nonRunningStoreResult") == "\"done\"" }
    }

    private fun controller(activity: MainActivity): NativeSettingsController {
        val field = MainActivity::class.java.getDeclaredField("settings\$delegate")
        field.isAccessible = true
        return (field.get(activity) as Lazy<*>).value as NativeSettingsController
    }
}
