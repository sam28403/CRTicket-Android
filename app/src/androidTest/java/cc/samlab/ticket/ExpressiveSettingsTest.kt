package cc.samlab.ticket

import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpressiveSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        compose.waitUntil(20_000) { evaluate("Boolean(window.crticketSettings)") == "true" }
        awaitRoute("/home")
    }

    @After fun close() {
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun nativeSettingsRenamesOwnerWithoutChangingTicketsAndPersistsOnReturn() {
        val original = JSONTokener(evaluate("JSON.parse(localStorage.getItem('user')).username")).nextValue() as String
        val records = evaluate("localStorage.getItem('crticket-device-tickets-v1')")
        try {
            openSettings()
            capture("settings")
            compose.onNodeWithText("显示名称").performClick()
            val field = compose.onNode(hasSetTextAction())
            field.performTextClearance()
            field.performTextInput("设置联通验证")
            compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) {
                evaluate("JSON.parse(localStorage.getItem('user')).username") == "\"设置联通验证\""
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("设置联通验证").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(records, evaluate("localStorage.getItem('crticket-device-tickets-v1')"))
            compose.onNodeWithContentDescription("返回").performClick()
            awaitRoute("/home")
            openSettings()
            compose.onNodeWithText("设置联通验证").assertIsDisplayed()
        } finally {
            evaluate("window.crticketSettings('restore-name','name',{username:${JSONObject.quote(original)}}); true")
            compose.waitUntil(10_000) {
                evaluate("JSON.parse(localStorage.getItem('user')).username") == JSONObject.quote(original)
            }
        }
    }

    @Test fun settingsDetailsAndPredictiveReturnRestoreHome() {
        openSettings()
        compose.onNodeWithText("到站检测", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("接受到访车站的通知").assertIsDisplayed()
        compose.onNodeWithText("启用到站检测").assertIsDisplayed()
        capture("arrival-settings")
        compose.onNodeWithText("管理相关权限").performScrollTo().performClick()
        compose.onNodeWithText("通知权限").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("启用到站检测").assertIsDisplayed()
        pressBack()
        awaitRoute("/settings")
        compose.onNodeWithText("导入备份", substring = false).performScrollTo().assertIsDisplayed()
        instrumentation.runOnMainSync {
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(140f, 300f, 0.7f, BackEventCompat.EDGE_LEFT))
            assertTrue(activity.findViewById<android.view.View>(R.id.expressive_settings_view).scaleX < 1f)
            dispatcher.onBackPressed()
        }
        awaitRoute("/home")
        compose.onNodeWithText("运转数据").performScrollTo().performClick()
        awaitRoute("/user")
        compose.waitUntil(20_000) {
            evaluate("Boolean(document.querySelector('.statistics-card'))") == "true"
        }
        assertEquals("0", evaluate("document.querySelectorAll('.avatar-card,.arrival-card,.backup-file-input').length"))
        pressBack()
        awaitRoute("/home")
    }

    @Test fun nativeAvatarPickerCropsAndSavesThroughWebStore() {
        val original = evaluate("JSON.parse(localStorage.getItem('user')).avatar")
        val image = File(activity.cacheDir, "settings-avatar-test.png")
        instrumentation.targetContext.resources.openRawResource(R.drawable.picture1).use { input ->
            image.outputStream().use { input.copyTo(it) }
        }
        val monitor = pickerResult("image/*", image)
        try {
            openSettings()
            compose.onNodeWithText("头像", substring = false).performClick()
            compose.onNodeWithText("选择图片").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("裁切头像").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("保存头像").performClick()
            compose.waitUntil(10_000) {
                evaluate("Boolean(JSON.parse(localStorage.getItem('user')).avatar?.startsWith('data:image/jpeg;base64,'))") == "true"
            }
            compose.onNodeWithContentDescription("当前头像").assertIsDisplayed()
        } finally {
            instrumentation.removeMonitor(monitor)
            image.delete()
            evaluate("window.crticketSettings('restore-avatar','avatar',{avatar:$original}); true")
            compose.waitUntil(10_000) { evaluate("JSON.parse(localStorage.getItem('user')).avatar") == original }
        }
    }

    @Test fun nativeBackupPickerConfirmsAndReportsInvalidRecordsWithoutChangingHistory() {
        val records = evaluate("localStorage.getItem('crticket-device-tickets-v1')")
        val backup = File(activity.cacheDir, "settings-backup-test.json").apply { writeText("[null]") }
        val monitor = pickerResult("application/json", backup)
        try {
            openSettings()
            evaluate("(() => { const request = window.crticketSettings; " +
                "window.crticketSettings = async (...args) => { " +
                "const result = await request(...args); window.__settingsTestResult = result; return result; }; })(); true")
            compose.onNodeWithText("导入备份").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("开始导入").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("开始导入").performClick()
            compose.waitUntil(10_000) {
                evaluate("window.__settingsTestResult?.message") == "\"导入完成：新增 0 条，略过 0 条，未处理 1 条\""
            }
            compose.onNodeWithText("导入完成：新增 0 条，略过 0 条，未处理 1 条").assertDoesNotExist()
            assertEquals(records, evaluate("localStorage.getItem('crticket-device-tickets-v1')"))
        } finally {
            instrumentation.removeMonitor(monitor)
            backup.delete()
        }
    }

    private fun pickerResult(type: String, file: File) = instrumentation.addMonitor(
        IntentFilter(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType(type)
        },
        ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))),
        true,
    )

    private fun openSettings() {
        compose.onNodeWithText("设置", substring = false).performScrollTo().performClick()
        awaitRoute("/settings")
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("正在读取…").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(5_000) {
            var settled = false
            instrumentation.runOnMainSync {
                val view = activity.findViewById<android.view.View>(R.id.expressive_settings_view)
                settled = view.alpha == 1f && view.translationX == 0f
            }
            settled
        }
        compose.onNodeWithText("个人资料").assertIsDisplayed()
    }

    private fun awaitRoute(route: String) {
        compose.waitUntil(20_000) { evaluate("location.hash.slice(1)") == "\"$route\"" }
        instrumentation.waitForIdleSync()
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
        val folder = File(activity.getExternalFilesDir(null), "expressive-settings-scenes").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
