package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import cc.samlab.ticket.ui.settings.debugTickets

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationTab
import cc.samlab.ticket.ui.trainlist.NativeTrainListController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NonRunningTrainsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun visibilityToggleRefreshesBothScreensAndUsesOneStrikeThrough() {
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val settings = controller(context, "settings") as NativeSettingsController
        val list = controller(context, "trainList") as NativeTrainListController
        val station = controller(context, "stationBoard") as NativeStationBoardController
        val preferences = context.getSharedPreferences("developer-options", 0)
        val versions = context.getSharedPreferences("data-versions", 0)
        val oldVersion = versions.getString("llt", null)
        val originalNetwork = NetworkFeatures.isAllowed()
        val originalAuto = preferences.getBoolean("lltAutoUpdate", true)
        val originalVisibility = settings.developer.showNonRunningTrains
        val file = File(context.cacheDir, "non-running-trains.db")
        val assets = fixture.instrumentation.context.assets
        fixture.evaluate(assets.open("non-running-store-fixture.js").bufferedReader().use { it.readText() })
        try {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, false)
                settings.developer.changeShowNonRunningTrains(false)
            }
            fixture.evaluate("window.dispatchEvent(new Event('crticket-network-changed')); " +
                "window.crticketTimetable.configure().then(() => window.nonRunningStore('save'))")
            awaitStore(fixture)
            assets.open("non-running-trains.db").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            fixture.instrumentation.runOnMainSync { settings.timetable.importFile(Uri.fromFile(file)) }
            compose.waitUntil(30_000) { !settings.timetable.busy }
            assertEquals("20000101", settings.timetable.version)
            fixture.evaluate("window.dispatchEvent(new Event('crticket-timetable-visibility-changed'))")

            fixture.navigate("/list")
            compose.waitUntil(20_000) { !list.loading && list.rows.size == 1 }
            assertEquals("G1", list.rows.single().train)
            setVisible(fixture, true)
            assertTrue(preferences.getBoolean("showNonRunningTrains", false))
            fixture.capture("non-running-developer")
            fixture.navigate("/list")
            compose.waitUntil(20_000) { !list.loading && list.rows.size == 2 }
            assertFalse(list.rows.first { it.train == "G1" }.notRunning)
            assertTrue(list.rows.first { it.train == "G2" }.notRunning)
            assertStrike("G1", "train-card:G1", false)
            assertStrike("G2", "train-card:G2", true)
            fixture.capture("non-running-list")

            val route = "/station?station=北京南"
            fixture.evaluate("window.crticketNavigate('$route')")
            val board = station.stateFor(route)
            compose.waitUntil(20_000) {
                !board.loading && board.tabs[StationTab.ALL]?.size == 2
            }
            fixture.instrumentation.runOnMainSync { board.follow = false }
            for (tab in listOf(StationTab.ALL, StationTab.DEPARTURE)) {
                compose.onNodeWithTag("station-tab-${tab.key}").performClick()
                val row = board.tabs.getValue(tab).single { it.train == "G2" }
                assertTrue(row.notRunning)
                assertFalse(row.inStation(12 * 3600))
                assertFalse(row.upcoming(12 * 3600))
                assertStrike("G2", "station-card-${row.id}", true)
            }
            fixture.capture("non-running-station")
            val inactive = board.tabs.getValue(StationTab.DEPARTURE).single { it.train == "G2" }
            assertTrue(inactive.route.contains("source=llt"))
            compose.onNode(hasTestTag("station-train-code") and
                hasAnyAncestor(hasTestTag("station-card-${inactive.id}")), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
                }
            } finally {
                fixture.capture("non-running-detail")
                println("Non-running detail route: " + fixture.evaluate("location.hash"))
                val queryController = controller(context, "trainQuery") as
                    cc.samlab.ticket.ui.trainquery.NativeTrainQueryController
                println("Non-running detail state: " + queryController.state.toString())
            }

            setVisible(fixture, false)
            fixture.navigate("/list")
            compose.waitUntil(20_000) { !list.loading && list.rows.size == 1 }
            fixture.evaluate("window.crticketNavigate('$route')")
            compose.waitUntil(20_000) {
                !board.loading && board.tabs[StationTab.ALL]?.size == 1
            }
            assertEquals("G1", board.tabs.getValue(StationTab.ALL).single().train)
        } finally {
            fixture.evaluate("window.nonRunningStore('restore')")
            awaitStore(fixture)
            fixture.instrumentation.runOnMainSync {
                settings.developer.changeShowNonRunningTrains(originalVisibility)
                NetworkFeatures.setEnabled(context, originalNetwork)
                preferences.edit().putBoolean("lltAutoUpdate", originalAuto).commit()
                val editor = versions.edit()
                if (oldVersion == null) editor.remove("llt") else editor.putString("llt", oldVersion)
                editor.commit()
            }
            file.delete()
            fixture.close()
        }
    }

    private fun setVisible(fixture: TabletPageFixture, enabled: Boolean) {
        fixture.navigate("/settings/developer")
        val title = fixture.activity.getString(R.string.developer_show_non_running_trains)
        val field = compose.onNodeWithContentDescription(title)
        field.performScrollTo()
        if (enabled) field.assertIsOff() else field.assertIsOn()
        field.performClick()
        if (enabled) field.assertIsOn() else field.assertIsOff()
    }

    private fun assertStrike(code: String, parent: String, expected: Boolean) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNode(hasText(code) and hasAnyAncestor(hasTestTag(parent)), useUnmergedTree = true)
        node.assert(SemanticsMatcher.expectValue(cc.samlab.ticket.ui.components.TrainCodeStrikeWidth,
            if (expected) 2f else 0f))
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val decoration = layouts.single().layoutInput.style.textDecoration
        assertFalse(decoration?.contains(TextDecoration.LineThrough) == true)
    }

    private fun awaitStore(fixture: TabletPageFixture) {
        compose.waitUntil(20_000) { fixture.evaluate("window.nonRunningStoreResult") == "\"done\"" }
    }

    private fun controller(activity: MainActivity, name: String): Any? {
        val field = MainActivity::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }
        return (field.get(activity) as Lazy<*>).value
    }
}
