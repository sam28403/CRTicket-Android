package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorNotifications
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.io.File
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class TicketMonitorWebViewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixture = MonitorLifecycleDeviceFixture(instrumentation)
    private val context = fixture.context

    @Test fun savedNativeStateRestoresControlsAndResultsAcrossPageNavigation() = fixture.withSnapshot {
        val config = JSONObject(MonitorTestPayloads.tickets()).put("interval", 3600)
        fixture.onMain { TicketMonitorService.start(context, config.toString()) }
        fixture.await("Initial native query did not complete", timeout = 60_000) {
            val status = TicketMonitorStore.status(context)
            status.optInt("attempts") > 0 && !status.optBoolean("loading")
        }
        val cached = TicketMonitorStore.read(context)
            .put("trains", JSONArray().put(JSONObject()
                .put("id", "native-cached-result").put("train", "TEST000")
                .put("from", "北京南").put("to", "上海虹桥")
                .put("departure", "09:00").put("arrival", "13:30")
                .put("duration", "04:30").put("canBuy", false)
                .put("status", "恢复验证缓存")
                .put("seats", JSONObject().put("second", "0"))))
            .put("queriedAt", Instant.now().toString())
        TicketMonitorStore.save(context, cached)
        navigate("/monitor")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("monitor-ticket-TEST000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("monitor-from").assertTextContains("北京南")
        compose.onNodeWithTag("monitor-to").assertTextContains("上海虹桥")
        compose.onNodeWithTag("monitor-toggle").assertIsOn()
        capture("monitor-results")
        navigate("/home")
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
        if (cc.samlab.ticket.notifications.NotificationAccess.enabled(context, "ticket-monitor-running")) {
            assertTrue(fixture.notice(TicketMonitorNotifications.ID) != null)
        }
        navigate("/monitor")
        compose.onNodeWithTag("monitor-toggle").assertIsOn()
        compose.onNodeWithTag("monitor-from").assertTextContains("北京南")
        compose.onNodeWithTag("monitor-settings").performClick()
        awaitRoute("/monitor/settings")
        compose.onNodeWithText("3600 秒").assertExists()
        pressBack()
        awaitRoute("/monitor")
        compose.onNodeWithTag("monitor-ticket-TEST000").assertExists()
    }

    @Test fun nativeSwitchStartsStopsAndReloadKeepsItStopped() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("monitor-from").assertIsEnabled(); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("monitor-from").performTextInput("beijingnan")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("北京南").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("北京南", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("monitor-to").performTextInput("shanghaihongqiao")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("上海虹桥").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("上海虹桥", useUnmergedTree = true).performClick()
        // Set a protective non-matching train filter through the same bridge as the settings UI.
        request("edit", "trainFilters", JSONArray().put("TEST000"))
        // Notification permission has separate coverage. Simulate its denied result
        // so this test exercises the switch without modifying the user's grants.
        fixture.evaluate("""
            (() => {
                const original = window.CRTicketAndroid;
                window.CRTicketAndroid = new Proxy({}, {
                    get(_target, key) {
                        if (key === 'requestNotificationPermission') {
                            return id => window.crticketNotificationResult(id, {granted: false});
                        }
                        const value = original[key];
                        return typeof value === 'function' ? value.bind(original) : value;
                    }
                });
            })()
        """.trimIndent())
        compose.onNodeWithTag("monitor-toggle").performClick()
        fixture.await("Native switch did not start monitor") {
            TicketMonitorStore.status(context).getBoolean("active")
        }
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("monitor-toggle").assertIsOn(); true }.getOrDefault(false)
        }
        assertEquals("TEST000", TicketMonitorStore.status(context).getJSONObject("config")
            .getJSONObject("filters").getJSONArray("trainFilters").getString(0))
        compose.onNodeWithTag("monitor-toggle").performClick()
        fixture.await("Native switch did not stop monitor") {
            !TicketMonitorStore.status(context).getBoolean("active") &&
                fixture.notice(TicketMonitorNotifications.ID) == null
        }
        fixture.evaluate("window.monitorReloadToken = 'before-reload'")
        fixture.reload()
        fixture.await("Document did not reload") {
            fixture.evaluate("typeof window.monitorReloadToken === 'undefined'") == "true"
        }
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("monitor-toggle").assertIsOff(); true }.getOrDefault(false)
        }
        assertFalse(TicketMonitorStore.status(context).getBoolean("active"))
    }

    @Test fun settingsEntryEditsFiltersAndReturnsToItsOrigin() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        compose.onNodeWithTag("monitor-settings").performClick()
        awaitRoute("/monitor/settings")
        compose.onNodeWithText("监控间隔").performClick()
        compose.onNodeWithTag("monitor-setting-input").performTextReplacement("30")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("30 秒").assertExists()
        compose.onNodeWithText("车次搜索").performScrollTo().performClick()
        compose.onNodeWithTag("monitor-setting-input").performTextReplacement("G1 G3")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("G1、G3").assertExists()
        capture("monitor-settings")
        compose.onNodeWithContentDescription("返回").performClick()
        awaitRoute("/monitor")
        compose.onNodeWithText("监控间隔").assertDoesNotExist()
        compose.onNodeWithTag("monitor-query").assertExists()
        navigate("/settings")
        compose.onNodeWithText("余票监控设置").performScrollTo().performClick()
        awaitRoute("/monitor/settings")
        compose.onNodeWithText("30 秒").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        awaitRoute("/settings")
    }

    @Test fun pullAtTopRequiresAnotherGestureAfterExpandingHeader() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        fixture.evaluate("""
            window.monitorRefreshCount = 0;
            window.addEventListener('crticket-monitor-page-request', event => {
                if (event.detail.action === 'refresh') window.monitorRefreshCount++;
            });
        """.trimIndent())
        compose.onNodeWithTag("monitor-results").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals("0", fixture.evaluate("window.monitorRefreshCount"))
        compose.onNodeWithTag("monitor-results").performTouchInput { swipeDown() }
        compose.waitUntil(10_000) { fixture.evaluate("window.monitorRefreshCount") == "1" }
        compose.onNodeWithText("请输入并选择有效的出发站和到达站").assertExists()
    }

    @Test fun cardsUseOneColumnOnPhoneAndMultipleColumnsWhenSpaceAllows() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        val rows = JSONArray()
        repeat(8) { index ->
            rows.put(JSONObject().put("id", "layout-$index").put("train", "G${index + 1}")
                .put("from", "北京南").put("to", "上海虹桥")
                .put("departure", "09:00").put("arrival", "13:30").put("duration", "04:30")
                .put("canBuy", true).put("seats", JSONObject().put("second", "有")))
        }
        val state = JSONObject().put("restoring", false).put("loading", false)
            .put("from", "北京南").put("to", "上海虹桥").put("date", "2026-10-17")
            .put("filtered", rows).put("queriedAt", Instant.now().toString())
            .put("columns", JSONArray().put(JSONArray().put("second").put("二等座")))
        fixture.evaluate("window.CRTicketAndroid.ticketMonitorPageResult(${JSONObject.quote(state.toString())})")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("monitor-ticket-G1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("monitor-results").performScrollToIndex(2)
        val first = compose.onNodeWithTag("monitor-ticket-G1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("monitor-ticket-G2").fetchSemanticsNode().boundsInRoot
        val width = compose.onNodeWithTag("monitor-results").fetchSemanticsNode().boundsInRoot.width
        val density = context.resources.displayMetrics.density
        if (width / density >= 684f) assertTrue(second.left > first.left)
        else assertTrue(second.top > first.top)
        capture("monitor-adaptive-results")
    }

    @Test fun resetClearsQueryFiltersAndSavedMonitorConfiguration() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        request("edit", "from", "北京南")
        request("edit", "to", "上海虹桥")
        request("edit", "interval", 30)
        request("edit", "trainFilters", JSONArray().put("G1"))
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("monitor-reset").assertIsEnabled(); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("monitor-reset").performClick()
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("monitor-from").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        }
        assertTrue(TicketMonitorStore.status(context).isNull("config"))
        compose.onNodeWithTag("monitor-empty").assertExists()
        compose.onNodeWithTag("monitor-empty-artwork").assertExists()
        compose.onNodeWithTag("monitor-settings").performClick()
        awaitRoute("/monitor/settings")
        compose.onNodeWithText("60 秒").assertExists()
        compose.onNodeWithText("G1").assertDoesNotExist()
    }

    @Test fun settingsAndMonitorShareNativePredictiveBackMotion() = fixture.withSnapshot {
        stopOriginal()
        navigate("/monitor")
        compose.onNodeWithTag("monitor-settings").performClick()
        awaitRoute("/monitor/settings")
        compose.waitForIdle()
        val original = compose.onNodeWithTag("navigation-page:/monitor/settings")
            .fetchSemanticsNode().boundsInRoot.width
        fixture.predictiveBack(null)
        fixture.predictiveBack(0.7f)
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("navigation-page:/monitor/settings")
                .fetchSemanticsNode().boundsInRoot.width < original - 1f
        }
        fixture.predictiveBack(null, cancel = true)
        compose.waitUntil(10_000) {
            kotlin.math.abs(compose.onNodeWithTag("navigation-page:/monitor/settings")
                .fetchSemanticsNode().boundsInRoot.width - original) < 1f
        }
        awaitRoute("/monitor/settings")
        pressBack()
        awaitRoute("/monitor")
        compose.onNodeWithTag("monitor-query").assertExists()
        val monitorWidth = compose.onNodeWithTag("navigation-page:/monitor")
            .fetchSemanticsNode().boundsInRoot.width
        fixture.predictiveBack(null)
        fixture.predictiveBack(0.7f)
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("navigation-page:/monitor").fetchSemanticsNode()
                .boundsInRoot.width < monitorWidth - 1f
        }
        fixture.predictiveBack(null, cancel = true)
        compose.waitUntil(10_000) {
            kotlin.math.abs(compose.onNodeWithTag("navigation-page:/monitor")
                .fetchSemanticsNode().boundsInRoot.width - monitorWidth) < 1f
        }
        pressBack()
        awaitRoute("/home")
    }

    @Test fun settingsDeepLinkSkipsMonitorAndUsesTheSameHorizontalMargins() = fixture.withSnapshot {
        stopOriginal()
        navigate("/settings")
        val settingsBounds = compose.onNodeWithTag("settings-content").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("余票监控设置").performScrollTo()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("余票监控设置").performClick()
            repeat(20) {
                compose.mainClock.advanceTimeBy(16)
                compose.onAllNodesWithTag("monitor-screen").assertCountEquals(0)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        awaitRoute("/monitor/settings")
        compose.onNodeWithTag("expressive-navigation").assertExists()
        compose.onNodeWithTag("monitor-query").assertDoesNotExist()
        val monitorBounds = compose.onNodeWithTag("monitor-settings-content").fetchSemanticsNode().boundsInRoot
        assertEquals(settingsBounds.left, monitorBounds.left, 1f)
        assertEquals(settingsBounds.right, monitorBounds.right, 1f)
        pressBack()
        awaitRoute("/settings")
    }

    @Test fun returnDrawsBothRealDestinationsInTheSameNavigationHost() = fixture.withSnapshot {
        stopOriginal()
        listOf("/settings", "/monitor").forEach { origin ->
            navigate(origin)
            var entryTop: Float? = null
            if (origin == "/settings") {
                val entry = compose.onNodeWithText("余票监控设置").performScrollTo()
                entryTop = entry.fetchSemanticsNode().boundsInRoot.top
                entry.performClick()
            } else compose.onNodeWithTag("monitor-settings").performClick()
            awaitRoute("/monitor/settings")
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            try {
                pressBack()
                var bothPagesDrawn = false
                repeat(24) { frame ->
                    compose.mainClock.advanceTimeBy(16)
                    val destination = compose.onAllNodesWithTag("navigation-page:$origin")
                        .fetchSemanticsNodes().any { it.boundsInRoot.width > 0f }
                    val outgoing = compose.onAllNodesWithTag("navigation-page:/monitor/settings")
                        .fetchSemanticsNodes().any { it.boundsInRoot.width > 0f }
                    assertTrue("Neither real page was drawn during return", destination || outgoing)
                    bothPagesDrawn = bothPagesDrawn || (destination && outgoing)
                    assertEquals("Navigation surface must never fade out", 1f, fixture.navigationViewAlpha())
                    if (frame == 4 || frame == 10) capture("return-${origin.removePrefix("/")}-$frame")
                }
                assertTrue("Return must overlap two real Navigation destinations", bothPagesDrawn)
            } finally {
                compose.mainClock.autoAdvance = true
            }
            awaitRoute(origin)
            compose.waitForIdle()
            compose.onNodeWithTag("monitor-settings-screen").assertDoesNotExist()
            entryTop?.let { expected ->
                assertEquals(
                    "Settings scroll position jumped on return", expected,
                    compose.onNodeWithText("余票监控设置").fetchSemanticsNode().boundsInRoot.top, 1f,
                )
            }
        }
    }

    private fun stopOriginal() {
        fixture.onMain { TicketMonitorService.stop(context) }
        TicketMonitorStore.save(context, JSONObject().put("active", false))
    }

    private fun request(action: String, key: String, value: Any) {
        val detail = JSONObject().put("action", action).put("key", key).put("value", value)
        fixture.evaluate("window.dispatchEvent(new CustomEvent('crticket-monitor-page-request',{detail:$detail}))")
    }

    private fun navigate(route: String) {
        fixture.await("Application router was not ready", timeout = 30_000) {
            fixture.evaluate("typeof window.crticketNavigate === 'function'") == "true"
        }
        fixture.evaluate("window.crticketNavigate(${JSONObject.quote(route)}).catch(error => { window.monitorNavError = String(error) })")
        awaitRoute(route)
        if (route.startsWith("/monitor")) compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(if (route.endsWith("settings")) "monitor-settings-screen" else "monitor-screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitRoute(route: String) {
        try {
            fixture.await("Did not reach $route", timeout = 30_000) {
                fixture.evaluate("location.hash === ${JSONObject.quote("#$route")}") == "true"
            }
        } catch (error: AssertionError) {
            throw AssertionError("Route: ${fixture.evaluate("location.hash")}; error: ${fixture.evaluate("window.monitorNavError")}", error)
        }
    }

    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(context.getExternalFilesDir(null), "monitor-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
