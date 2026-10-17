package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
        }
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
        evaluate("""
            window.historyRouterReady = false;
            document.getElementById('app').__vue_app__.config.globalProperties
                .${'$'}router.isReady().then(() => { window.historyRouterReady = true; });
        """.trimIndent())
        compose.waitUntil(10_000) { evaluate("window.historyRouterReady") == "true" }
        evaluate("""
            window.historyPageReady = false;
            document.getElementById('app').__vue_app__.config.globalProperties
                .${'$'}router.push('/history').then(() => { window.historyPageReady = true; });
        """.trimIndent())
        compose.waitUntil(10_000) { evaluate("window.historyPageReady") == "true" }
        try {
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag("history-pull").fetchSemanticsNode()
                        .config[SemanticsProperties.StateDescription] == "历史记录已加载"
                }.getOrDefault(false)
            }
        } catch (error: Throwable) {
            capture("history-start-failure")
            println("HISTORY_START: " + evaluate("[location.hash, Boolean(document.querySelector('.history-page'))]"))
            instrumentation.runOnMainSync {
                println("HISTORY_VIEWS: " + activity.findViewById<android.view.View>(R.id.expressive_navigation_view).visibility +
                    ":" + activity.webView.visibility)
            }
            throw error
        }
        evaluate("""
            window.historyTestAction = null;
            window.addEventListener('crticket-history-action', event => {
                window.historyTestAction = event.detail;
            });
            CRTicketAndroid.historyResult(JSON.stringify({
                loading: false, error: '', distance: 2486, money: 1215.5,
                defaults: {number: 'CODEX_HISTORY_TEST', trainNo: 'G103', from: '北京南', to: '上海虹桥',
                    date: '2026年10月16日', time: '09:00', price: '553', seatType: '二等座',
                    seatNo: '05车08A号', sellPlace: '北京南站', theme: 'EMU_Green.jpg',
                    distance: '1318', message: '界面测试记录'},
                rows: [
                    {id: 'demo1', date: '2026年10月16日', time: '09:00', trainNo: 'G103',
                     from: '北京南', to: '上海虹桥', seatType: '二等座', seatNo: '05车08A号',
                     price: 553, number: 'E123456789', useCredit: 0},
                    {id: 'demo2', date: '2026年10月12日', time: '14:26', trainNo: 'G7507',
                     from: '上海虹桥', to: '杭州东', seatType: '二等座', seatNo: '03车12F号',
                     price: 73, number: 'E987654321', useCredit: 0},
                    {id: 'demo3', date: '2026年10月08日', time: '08:15', trainNo: 'G58',
                     from: '宁波', to: '北京南', seatType: '二等座', seatNo: '08车06A号',
                     price: 589.5, number: 'E351822734', useCredit: 0}
                ]
            }));
            CRTicketAndroid.setNativePage('/history');
        """.trimIndent())
        compose.waitUntil(10_000) {
            runCatching {
                compose.onAllNodesWithTag("history-record-demo1").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @After fun close() {
        // Remove only the record owned by this test; never replace the user's database.
        evaluate("""
            const testRecords = JSON.parse(localStorage.getItem('crticket-device-tickets-v1') || '[]');
            localStorage.setItem('crticket-device-tickets-v1', JSON.stringify(
                testRecords.filter(row => row.ticket_number !== 'CODEX_HISTORY_TEST')
            ));
        """.trimIndent())
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun emptyHistoryUsesExpressiveArtworkAndOriginalPrompt() {
        evaluate("""
            CRTicketAndroid.historyResult(JSON.stringify({
                loading: false, error: '', distance: 0, money: 0, rows: []
            }));
        """.trimIndent())
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("history-empty").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("history-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history-empty-artwork").assertIsDisplayed()
        compose.onNodeWithText("还没有运转记录，补登一段旅程吧。").assertIsDisplayed()
        compose.onNodeWithText("暂无运转数据").assertDoesNotExist()
        capture("history-empty")
        compose.onNodeWithText("补登记录").performScrollTo().performClick()
        compose.onNodeWithTag("history-editor").assertExists()
        compose.onNodeWithTag("history-empty").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("history-empty").performScrollTo().assertIsDisplayed()
    }

    @Test fun cardsFitWithoutHorizontalScrollAndAllSortDirectionsWork() {
        val list = compose.onNodeWithTag("history-list")
        assertFalse(list.fetchSemanticsNode().config.contains(SemanticsProperties.HorizontalScrollAxisRange))
        val price = compose.onAllNodesWithTag("history-price").onFirst().fetchSemanticsNode().boundsInRoot
        val seat = compose.onAllNodesWithTag("history-seat").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(price.left < seat.left)
        compose.onNodeWithText("05车08A号 · 二等座").assertExists()
        compose.onNodeWithText("Hi,", substring = true).assertDoesNotExist()
        capture("history-phone")
        choose("日期从远到近")
        assertFirst("G58")
        choose("票价从低到高")
        assertFirst("G7507")
        choose("票价从高到低")
        assertFirst("G58")
        choose("日期从近到远")
        assertFirst("G103")
        compose.onNodeWithContentDescription("历史记录排序").performClick()
        capture("history-sort")
    }

    @Test fun nativeEditorValidatesAndBackReturnsToHistory() {
        compose.onAllNodesWithContentDescription("记录操作").onFirst().performClick()
        compose.onNodeWithText("编辑记录").performClick()
        compose.onNodeWithTag("history-editor").assertExists()
        compose.onNodeWithTag("history-field-trainNo").assertTextContains("G103")
        compose.onNodeWithTag("history-field-trainNo").performTextReplacement("invalid")
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("请输入正确的车次").fetchSemanticsNodes().isNotEmpty()
        }
        capture("history-editor-error")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("history-list").assertExists()
        compose.onNodeWithTag("history-editor").assertDoesNotExist()
        assertEquals("true", evaluate("location.hash === '#/history'"))
        compose.onNodeWithText("补登记录").performClick()
        compose.onNodeWithTag("history-field-number").assertTextContains("CODEX_HISTORY_TEST")
        capture("history-create")
        compose.onNodeWithTag("history-field-date").performScrollTo().performClick()
        compose.onNodeWithText("确定").assertExists()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("history-field-time").performScrollTo().performClick()
        compose.onNodeWithText("确定").assertExists()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
    }

    @Test fun nativeCreateEditDeletePersistAndRefreshKeepsSort() {
        compose.onNodeWithText("补登记录").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(15_000) {
            evaluate("JSON.parse(localStorage.getItem('crticket-device-tickets-v1') || '[]').some(r => r.ticket_number === 'CODEX_HISTORY_TEST')") == "true"
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
        }
        val id = evaluate("JSON.parse(localStorage.getItem('crticket-device-tickets-v1')).find(r => r.ticket_number === 'CODEX_HISTORY_TEST').id")
            .trim('"')
        val record = compose.onNodeWithTag("history-record-$id")
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-record-$id"))
        compose.onNode(hasContentDescription("记录操作") and hasAnyAncestor(hasTestTag("history-record-$id")))
            .performClick()
        compose.onNodeWithText("编辑记录").performClick()
        compose.onNodeWithTag("history-field-price").performScrollTo().performTextReplacement("123.5")
        // Hide the keyboard before saving through the always-visible app bar.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(15_000) {
            evaluate("JSON.parse(localStorage.getItem('crticket-device-tickets-v1')).find(r => r.id === '$id').price") == "123.5"
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
        }
        choose("票价从低到高")
        compose.onNodeWithTag("history-list").performTouchInput { swipeDown() }
        compose.waitUntil(10_000) { evaluate("window.historyTestAction?.action") == "\"refresh\"" }
        compose.onNodeWithContentDescription("历史记录排序").performClick()
        compose.onNodeWithText("票价从低到高").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-record-$id"))
        compose.onNode(hasContentDescription("记录操作") and hasAnyAncestor(hasTestTag("history-record-$id")))
            .performClick()
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithTag("history-delete-dialog").assertExists()
        capture("history-delete")
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        record.assertExists()
        compose.onNode(hasContentDescription("记录操作") and hasAnyAncestor(hasTestTag("history-record-$id")))
            .performClick()
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.waitUntil(15_000) {
            evaluate("JSON.parse(localStorage.getItem('crticket-device-tickets-v1')).some(r => r.id === '$id')") == "false"
        }
    }

    @Test fun creditSwitchConvertsPriceInCreateAndEdit() {
        for (mode in listOf("create", "edit")) {
            if (mode == "create") compose.onNodeWithText("补登记录").performClick()
            else {
                compose.onAllNodesWithContentDescription("记录操作").onFirst().performClick()
                compose.onNodeWithText("编辑记录").performClick()
            }
            val price = compose.onNodeWithTag("history-field-price")
            val credit = compose.onNodeWithTag("history-switch-使用积分")
            price.performScrollTo().assertTextContains("553")
            credit.performScrollTo().performClick()
            price.performScrollTo().assertTextContains("55300")
            credit.performScrollTo().performClick()
            price.performScrollTo().assertTextContains("553")
            compose.onNodeWithContentDescription("返回").performClick()
        }
    }

    @Test fun dateAndTimePickersWorkInCreateAndEdit() {
        for (mode in listOf("create", "edit")) {
            if (mode == "create") {
                compose.onNodeWithText("补登记录").performClick()
            } else {
                compose.onAllNodesWithContentDescription("记录操作").onFirst().performClick()
                compose.onNodeWithText("编辑记录").performClick()
            }
            val date = compose.onNodeWithTag("history-field-date")
            date.performScrollTo().assertTextContains("2026.10.16")
            date.performClick()
            compose.onNode(hasText("17") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("确定").performClick()
            date.assertTextContains("2026.10.17")
            date.performClick()
            compose.onNodeWithText("确定").performClick()
            date.assertTextContains("2026.10.17")

            val time = compose.onNodeWithTag("history-field-time")
            time.performScrollTo().performClick()
            compose.onNodeWithTag("history-time-clock").assertExists()
            compose.onNodeWithTag("history-time-mode-toggle").performClick()
            compose.onNodeWithTag("history-time-input").assertExists()
            val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
            inputs[0].performTextReplacement("23")
            inputs[1].performTextReplacement("45")
            compose.onNodeWithText("确定").performClick()
            time.assertTextContains("23:45")

            time.performClick()
            compose.onNodeWithTag("history-time-clock").assertExists()
            compose.onNodeWithTag("history-time-mode-toggle").performClick()
            compose.onNodeWithTag("history-time-mode-toggle").performClick()
            compose.onNodeWithTag("history-time-clock").assertExists()
            compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
            time.assertTextContains("23:45")
            capture("history-$mode-date-time")
            compose.onNodeWithContentDescription("返回").performClick()
        }
    }

    private fun choose(label: String) {
        compose.onNodeWithContentDescription("历史记录排序").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun assertFirst(train: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("history-list").onChildren()[1]
            .assert(hasAnyDescendant(hasText(train)) or hasText(train))
    }

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { result = it; done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(activity.getExternalFilesDir(null), "history-preview").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
