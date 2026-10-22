package cc.samlab.ticket

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertTrue

internal class BackupDeviceFixture(
    private val compose: ComposeTestRule,
    private val activity: MainActivity,
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val keys = listOf(
        "crticket-device-tickets-v1", "crticket-device-owner-v1", "crticket-default-owner:device",
        "user", "login", "crticket-theme", "crticket-important-notice-hidden",
        "crticket-train-query-history", "crticket-station-query-history",
        "crticket-generator-draft", "crticket-generator-receipt",
    )

    fun ready() {
        compose.waitUntil(20_000) { evaluate("typeof window.crticketSettings === 'function'") == "true" }
        compose.onNodeWithText("设置", substring = false).performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("正在读取…").fetchSemanticsNodes().isEmpty() &&
                evaluate("location.hash === '#/settings'") == "true"
        }
        evaluate("""
            (() => {
              const original = window.crticketSettings;
              window.crticketSettings = async (...args) => {
                const result = await original(...args);
                if (args[1] === 'import' || args[1] === 'exportBackup') window.__backupTestResult = result;
                return result;
              };
            })(); true
        """.trimIndent())
    }

    fun storage(): JSONObject = JSONObject(stringValue(evaluate(
        "JSON.stringify(Object.fromEntries(${org.json.JSONArray(keys)}.map(key => [key, localStorage.getItem(key)])))",
    )))

    fun restoreStorage(value: JSONObject) {
        evaluate("""
            Object.entries($value).forEach(([key, value]) => {
              if (value === null) localStorage.removeItem(key);
              else localStorage.setItem(key, value);
            }); true
        """.trimIndent())
    }

    fun export(excluded: Set<String> = emptySet()): JSONObject {
        evaluate("""
            window.__backupTestResult = null;
            window.__originalBlob = window.Blob;
            window.Blob = new Proxy(window.Blob, {
              construct(target, args) {
                if (args[1]?.type?.startsWith('application/json')) window.__backupExport = args[0][0];
                return Reflect.construct(target, args);
              }
            }); true
        """.trimIndent())
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("application/json")
            },
            ActivityResult(Activity.RESULT_CANCELED, null), true,
        )
        try {
            compose.onNodeWithText("导出备份").performScrollTo().performClick()
            capture("export-selection.png")
            excluded.forEach { label ->
                compose.onNode(hasText(label) and isToggleable()).assertIsOn().performScrollTo().performClick()
            }
            compose.onNodeWithText("开始导出").performClick()
            result()
            return JSONObject(stringValue(evaluate("window.__backupExport")))
        } finally {
            instrumentation.removeMonitor(monitor)
            evaluate("window.Blob = window.__originalBlob; true")
        }
    }

    fun import(value: String, settings: Boolean, excluded: Set<String> = emptySet()): JSONObject {
        val file = File(activity.cacheDir, "full-backup-device-test.json").apply { writeText(value) }
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("application/json")
            },
            ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))), true,
        )
        try {
            evaluate("window.__backupTestResult = null; true")
            compose.onNodeWithText("导入备份").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("开始导入").fetchSemanticsNodes().isNotEmpty()
            }
            if (settings) {
                capture()
            }
            excluded.forEach { label ->
                compose.onNode(hasText(label) and isToggleable()).assertIsOn().performScrollTo().performClick()
            }
            compose.onNodeWithText("开始导入").performClick()
            return result()
        } finally {
            instrumentation.removeMonitor(monitor)
            file.delete()
        }
    }

    private fun result(): JSONObject {
        compose.waitUntil(20_000) { evaluate("Boolean(window.__backupTestResult)") == "true" }
        return JSONObject(evaluate("window.__backupTestResult"))
    }

    fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { result = it; done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    fun stringValue(value: String): String = JSONTokener(value).nextValue() as String

    private fun capture(filename: String = "confirmation.png") {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val folder = File(activity.getExternalFilesDir(null), "full-backup-scenes").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, filename).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
