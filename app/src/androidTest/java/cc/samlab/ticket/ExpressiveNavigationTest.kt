package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressiveNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val fixture = MonitorLifecycleDeviceFixture(InstrumentationRegistry.getInstrumentation())

    @Test fun helpTopicsReturnToIndexInTheNativeStack() = fixture.withSnapshot {
        fixture.await("Router was not ready") {
            fixture.evaluate("typeof window.crticketNavigate === 'function'") == "true"
        }
        compose.onNodeWithText("帮助", substring = false).performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("help-index").fetchSemanticsNodes().isNotEmpty()
        }
        listOf(
            "tickets" to "车票制作与历史记录",
            "backup" to "备份、恢复与本机数据",
            "speed" to "车速表与轨迹",
            "lines" to "线路查询与普速票价",
            "quiz" to "每日小测验",
        ).forEach { (id, title) ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithTag("help-$id").assertExists()
            compose.onNodeWithText("使用规则").assertExists()
            pressBack()
            compose.onNodeWithTag("help-index").assertExists()
        }
    }

    @Test fun everyExpressivePageUsesTheSameNavigationGraph() = fixture.withSnapshot {
        listOf("/about", "/settings", "/history", "/list", "/station", "/train", "/user", "/monitor")
            .forEach { route ->
                navigate(route)
                compose.onAllNodesWithTag("expressive-navigation").assertCountEquals(1)
                compose.onNodeWithTag("navigation-page:$route").assertExists()
                assertEquals(1f, fixture.navigationViewAlpha())
                pressBack()
                awaitRoute("/home")
            }
    }

    @Test fun settingsChildrenAndHistoryEditorAreSeparateNavigationDestinations() = fixture.withSnapshot {
        navigate("/settings")
        compose.onNodeWithText("到站检测", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("navigation-page:/settings/arrival").assertExists()
        compose.onNodeWithText("管理相关权限").performScrollTo().performClick()
        compose.onNodeWithTag("navigation-page:/settings/permissions").assertExists()
        pressBack()
        compose.onNodeWithTag("navigation-page:/settings/arrival").assertExists()
        pressBack()
        compose.onNodeWithTag("navigation-page:/settings").assertExists()
        pressBack()
        awaitRoute("/home")
        navigate("/history")
        compose.onNodeWithText("补登记录", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("navigation-page:/history/editor").assertExists()
        pressBack()
        compose.onNodeWithTag("history-list").assertExists()
        compose.onNodeWithTag("navigation-page:/history/editor").assertDoesNotExist()
    }

    @Test fun queryArgumentsKeepDistinctEntriesAndBackRestoresThePreviousLocation() = fixture.withSnapshot {
        val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
        navigate("/train?train=G1&date=$date")
        navigate("/train?train=G3&date=$date")
        pressBack()
        awaitRoute("/train?train=G1&date=$date")
        compose.onNodeWithTag("navigation-page:/train?train=G1&date=$date").assertExists()
        pressBack()
        awaitRoute("/home")
    }

    @Test fun predictiveBackKeepsAPageSizedPreviewAndCancelsOnEveryNativePage() = fixture.withSnapshot {
        listOf("/about", "/settings", "/history", "/train", "/monitor").forEach { route ->
            println("PREDICTIVE_ROUTE=$route")
            navigate(route)
            val page = compose.onNodeWithTag("navigation-page:$route")
            val width = page.fetchSemanticsNode().boundsInRoot.width
            compose.mainClock.autoAdvance = false
            try {
                fixture.predictiveBack(null)
                fixture.predictiveBack(0.6f)
                compose.mainClock.advanceTimeBy(160)
                org.junit.Assert.assertTrue("$route did not follow predictive progress",
                    page.fetchSemanticsNode().boundsInRoot.width < width - 1f)
                fixture.predictiveBack(0.98f)
                compose.mainClock.advanceTimeBy(160)
                org.junit.Assert.assertTrue("$route shrank beyond the system preview size",
                    page.fetchSemanticsNode().boundsInRoot.width > width * 0.89f)
                compose.onNodeWithTag("navigation-page:/home").assertExists()
                fixture.predictiveBack(null, cancel = true)
                compose.mainClock.advanceTimeBy(1_000)
                assertEquals("$route did not restore after cancellation", width,
                    page.fetchSemanticsNode().boundsInRoot.width, 1f)
                assertEquals("true", fixture.evaluate("location.hash === ${JSONObject.quote("#$route")}"))
                fixture.predictiveBack(null)
                fixture.predictiveBack(0.8f)
                compose.mainClock.advanceTimeBy(160)
                fixture.commitBack()
                compose.mainClock.advanceTimeBy(1_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }
            awaitRoute("/home")
        }
    }

    @Test fun predictiveBackUsesTheGestureEdgeAndRestoresGeometryOnCancellation() = fixture.withSnapshot {
        navigate("/about")
        val page = compose.onNodeWithTag("navigation-page:/about")
        val original = page.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            listOf(androidx.activity.BackEventCompat.EDGE_LEFT, androidx.activity.BackEventCompat.EDGE_RIGHT)
                .forEach { edge ->
                    fixture.predictiveBack(null, edge = edge)
                    fixture.predictiveBack(0.8f, edge = edge)
                    compose.mainClock.advanceTimeBy(160)
                    val preview = page.fetchSemanticsNode().boundsInRoot
                    if (edge == androidx.activity.BackEventCompat.EDGE_LEFT) {
                        org.junit.Assert.assertTrue("Left-edge preview did not move right",
                            preview.center.x > original.center.x + 1f)
                    } else {
                        assertEquals("Right-edge preview must stay centered",
                            original.center.x, preview.center.x, 1f)
                    }
                    fixture.predictiveBack(null, cancel = true, edge = edge)
                    compose.mainClock.advanceTimeBy(1_000)
                    val restored = page.fetchSemanticsNode().boundsInRoot
                    assertEquals(original.width, restored.width, 1f)
                    assertEquals(original.center.x, restored.center.x, 1f)
                    assertEquals("true", fixture.evaluate("location.hash === '#/about'"))
                }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun predictiveReleaseGetsAFullSystemDurationAtDifferentGestureProgress() = fixture.withSnapshot {
        listOf(0.2f, 0.8f).forEach { progress ->
            navigate("/about")
            compose.mainClock.autoAdvance = false
            try {
                fixture.predictiveBack(null)
                fixture.predictiveBack(progress)
                compose.mainClock.advanceTimeBy(64)
                fixture.commitBack()
                compose.mainClock.advanceTimeBy(416)
                compose.onNodeWithTag("navigation-page:/about").assertExists()
                assertEquals("Release was shortened by gesture progress $progress", "true",
                    fixture.evaluate("location.hash === '#/about'"))
                compose.mainClock.advanceTimeBy(128)
                compose.onNodeWithTag("navigation-page:/about").assertDoesNotExist()
            } finally {
                compose.mainClock.autoAdvance = true
            }
            awaitRoute("/home")
        }
    }

    @Test fun backDuringAnUnfinishedEntryRetainsTheOutgoingPageOwners() = fixture.withSnapshot {
        navigate("/settings")
        compose.mainClock.autoAdvance = false
        try {
            navigate("/about")
            compose.mainClock.advanceTimeBy(32)
            fixture.commitBack()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("navigation-page:/settings").assertExists()
            compose.onNodeWithTag("navigation-page:/about").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        awaitRoute("/settings")
    }

    private fun navigate(route: String) {
        fixture.await("Router was not ready") {
            fixture.evaluate("typeof window.crticketNavigate === 'function'") == "true"
        }
        fixture.evaluate("window.crticketNavigate(${JSONObject.quote(route)}).catch(error => { window.navigationTestError = String(error) })")
        awaitRoute(route)
    }

    private fun awaitRoute(route: String) {
        try {
            fixture.await("Did not reach $route") {
                fixture.evaluate("location.hash === ${JSONObject.quote("#$route")}") == "true"
            }
        } catch (error: AssertionError) {
            throw AssertionError("Expected $route; actual ${fixture.evaluate("location.hash")}; error ${fixture.evaluate("window.navigationTestError")}", error)
        }
        compose.waitForIdle()
    }
}
