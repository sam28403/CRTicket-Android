package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import cc.samlab.ticket.ui.settings.debugTickets

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.monitor.TicketMonitorStore
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NetworkFeaturesDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun railwayTravelRequiresNetworking() {
        val fixture = TabletPageFixture(compose)
        val activity = fixture.activity
        val original = NetworkFeatures.isAllowed()
        val preferences = activity.getSharedPreferences("developer-options", 0)
        val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        val hadAutoUpdate = preferences.contains("lltAutoUpdate")
        val navigateTo = MainActivity::class.java.getDeclaredMethod("navigateTo", String::class.java).apply {
            isAccessible = true
        }
        fun navigate(route: String) {
            fixture.instrumentation.runOnMainSync { navigateTo.invoke(activity, route) }
        }
        fun assertTravelBlocked(route: String) {
            val page = hasTestTag("navigation-page:$route")
            val message = activity.getString(R.string.network_features_disabled)
            val blocked = compose.onNode(hasText(message) and hasAnyAncestor(page))
            compose.waitUntil(10_000) { blocked.isDisplayed() }
            compose.onNodeWithTag("travel-pull").assertDoesNotExist()
        }

        try {
            fixture.instrumentation.runOnMainSync { NetworkFeatures.setEnabled(activity, true) }
            navigate("/home")
            val entry = compose.onNode(hasText(activity.getString(R.string.travel_title)) and hasClickAction())
            entry.performScrollTo().assertIsEnabled().performClick()
            val travel = compose.onNodeWithTag("travel-pull")
            compose.waitUntil(10_000) { travel.isDisplayed() }

            fixture.instrumentation.runOnMainSync { NetworkFeatures.setEnabled(activity, false) }
            assertTravelBlocked("/travel")
            val article = "/travel/article?file=test.md"
            navigate(article)
            assertTravelBlocked(article)

            navigate("/home")
            entry.performScrollTo().assertIsNotEnabled()
            fixture.instrumentation.runOnMainSync { NetworkFeatures.setEnabled(activity, true) }
            entry.assertIsEnabled()
        } finally {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(activity, original)
                val editor = preferences.edit()
                if (hadAutoUpdate) editor.putBoolean("lltAutoUpdate", autoUpdate) else editor.remove("lltAutoUpdate")
                editor.commit()
            }
            fixture.close()
        }
    }

    @Test fun homeOpensTrainQueryDirectlyInOfflineMode() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = NetworkFeatures.isAllowed()
        val preferences = context.getSharedPreferences("developer-options", 0)
        val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        WelcomeTestState(context).use {
            val fixture = TabletPageFixture(compose)
            try {
                instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, false) }
                fixture.evaluate("window.dispatchEvent(new Event('crticket-network-changed'))")
                fixture.navigate("/home")
                fixture.evaluate("""
                    window.trainEntries = [];
                    for (const method of ['pushState', 'replaceState']) {
                        const commit = history[method].bind(history);
                        history[method] = (...args) => {
                            const previous = location.hash;
                            commit(...args);
                            if (location.hash !== previous && /^#\/train(?:\?|$)/.test(location.hash)) {
                                window.trainEntries.push(location.hash);
                            }
                        };
                    }
                """.trimIndent())
                compose.onNode(hasText(fixture.activity.getString(R.string.home_train)) and hasClickAction())
                    .performScrollTo().performClick()
                compose.waitUntil(20_000) {
                    fixture.evaluate("location.hash") == "\"#/train?source=llt\""
                }
                compose.onNodeWithTag("train-query-source").assertIsNotEnabled()
                assertEquals("[\"#/train?source=llt\"]", fixture.evaluate("window.trainEntries"))
            } finally {
                instrumentation.runOnMainSync {
                    NetworkFeatures.setEnabled(context, original)
                    preferences.edit().putBoolean("lltAutoUpdate", autoUpdate).commit()
                }
                fixture.close()
            }
        }
    }

    @Test fun onlineSwitchBlocksFeaturesAndKeepsOfflineTrainQueries() {
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val original = NetworkFeatures.isAllowed()
        val settingsField = MainActivity::class.java.getDeclaredField("settings\$delegate").apply {
            isAccessible = true
        }
        val settings = (settingsField.get(context) as Lazy<*>).value as
            cc.samlab.ticket.ui.settings.NativeSettingsController
        val autoUpdate = settings.timetable.autoUpdate
        val railfan = RailfanPreferences.enabled(context)
        try {
            fixture.evaluate("window.networkConfigure = window.crticketTimetable.configure; " +
                "window.crticketTimetable.configure = () => {}")
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, true)
                settings.timetable.changeAutoUpdate(true)
                RailfanPreferences.setEnabled(context, true)
            }
            fixture.navigate("/settings/developer")
            val title = context.getString(R.string.network_features_enabled)
            fixture.evaluate("window.crticketTimetable.configure = window.networkConfigure")
            compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
            assertFalse(NetworkFeatures.isAllowed())
            assertFalse(TicketMonitorStore.read(context).optBoolean("active"))
            assertEquals("false", fixture.evaluate("CRTicketDeveloper.networkEnabled()"))
            val automatic = context.getString(R.string.llt_auto_update)
            compose.onNodeWithContentDescription(automatic).assertIsOff().assertIsNotEnabled()
            assertFalse(context.getSharedPreferences("developer-options", 0).getBoolean("lltAutoUpdate", true))
            fixture.instrumentation.runOnMainSync { settings.timetable.changeAutoUpdate(true) }
            assertFalse(settings.timetable.autoUpdate)
            compose.onNode(hasText(context.getString(R.string.llt_import_title)) and hasClickAction())
                .performScrollTo().assertIsEnabled()
            fixture.capture("network-features-off")

            fixture.navigate("/settings/developer/tickets")
            compose.onNodeWithTag("debug-tickets-generate").assertIsNotEnabled()

            fixture.navigate("/home")
            fixture.navigate("/settings")
            fixture.capture("network-settings-map-disabled")
            compose.onNodeWithText(context.getString(R.string.settings_map))
                .performScrollTo().assertIsNotEnabled()
            fixture.navigate("/settings/map")
            compose.onNodeWithText(context.getString(R.string.network_features_disabled)).assertExists()
            fixture.navigate("/station")
            val boardField = MainActivity::class.java.getDeclaredField("stationBoard\$delegate").apply {
                isAccessible = true
            }
            val boards = (boardField.get(context) as Lazy<*>).value as
                cc.samlab.ticket.ui.station.NativeStationBoardController
            val board = boards.stateFor("/station")
            fixture.instrumentation.runOnMainSync {
                board.date = java.time.LocalDate.now().toString()
                board.resultStation = "北京"
                board.selectedTab = cc.samlab.ticket.ui.station.StationTab.MAP
            }
            compose.waitForIdle()
            compose.onNodeWithTag("station-tab-map").assertIsNotEnabled()
            compose.onNodeWithTag("station-map-settings").assertIsNotEnabled()
            compose.onAllNodesWithTag("station-map").assertCountEquals(0)
            assertEquals(cc.samlab.ticket.ui.station.StationTab.ALL, board.selectedTab)
            fixture.capture("network-station-map-disabled")

            fixture.navigate("/home")
            for (id in listOf(R.string.home_monitor, R.string.railfan_vehicle,
                R.string.railfan_lines, R.string.quiz_title)) {
                compose.onNode(hasText(context.getString(id)) and hasClickAction())
                    .performScrollTo().assertIsNotEnabled()
            }
            fixture.capture("network-home-disabled")
            for (route in listOf("/monitor", "/railfan/vehicle", "/railfan/lines", "/railfan/quiz")) {
                fixture.navigate(route)
                compose.onNodeWithText(context.getString(R.string.network_features_disabled)).assertExists()
            }
            fixture.evaluate("""
                window.crticketTimetable.snapshot().then(version => {
                    window.networkFixtureDate = version?.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3');
                })
            """.trimIndent())
            compose.waitUntil(20_000) {
                fixture.evaluate("typeof window.networkFixtureDate === 'string'") == "true"
            }
            val date = fixture.evaluate("window.networkFixtureDate").trim('"')
            fixture.evaluate("window.crticketNavigate('/train?train=G1&date=$date')")
            compose.waitUntil(20_000) {
                fixture.evaluate("location.hash.includes('source=llt')") == "true"
            }
            compose.onNodeWithTag("train-query-source").assertIsNotEnabled()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("train-query-summary").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.capture("network-offline-train")

            fixture.navigate("/settings/developer")
            compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
            assertTrue(NetworkFeatures.isAllowed())
            compose.onNodeWithContentDescription(automatic).assertIsOff().assertIsEnabled()
            fixture.navigate("/home")
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_map)) and hasClickAction())
                .performScrollTo().assertIsEnabled()
            fixture.navigate("/station")
            compose.onNodeWithTag("station-tab-map").assertIsEnabled()
            compose.onNodeWithTag("station-map-settings").assertIsEnabled()
            fixture.navigate("/home")
            compose.onNode(hasText(context.getString(R.string.home_monitor)) and hasClickAction())
                .performScrollTo().assertIsEnabled()
        } finally {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, original)
                settings.timetable.changeAutoUpdate(autoUpdate)
                RailfanPreferences.setEnabled(context, railfan)
            }
            fixture.close()
        }
    }

    @Test fun httpGateRejectsRequestsBeforeTransportAndCanBeReenabled() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = NetworkFeatures.isAllowed()
        val preferences = context.getSharedPreferences("developer-options", 0)
        val autoUpdate = preferences.getBoolean("lltAutoUpdate", true)
        val hadAutoUpdate = preferences.contains("lltAutoUpdate")
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkFeatures.interceptor)
            .eventListenerFactory(NetworkFeatures.events)
            .addInterceptor { chain ->
                requests.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("fixture".toResponseBody()).build()
            }.build()
        val request = Request.Builder().url("https://example.invalid/").build()
        try {
            instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, false) }
            assertTrue(NetworkFeatures.blocksRoute("/travel"))
            assertTrue(NetworkFeatures.blocksRoute("/travel/article?file=test.md"))
            try {
                client.newCall(request).execute().close()
                fail("Disabled networking reached the transport")
            } catch (_: IOException) { }
            assertEquals(0, requests.get())
            instrumentation.runOnMainSync { NetworkFeatures.setEnabled(context, true) }
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals(1, requests.get())
        } finally {
            instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, original)
                val editor = preferences.edit()
                if (hadAutoUpdate) editor.putBoolean("lltAutoUpdate", autoUpdate) else editor.remove("lltAutoUpdate")
                editor.commit()
            }
        }
    }
}
