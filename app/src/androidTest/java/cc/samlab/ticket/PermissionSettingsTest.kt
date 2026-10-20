package cc.samlab.ticket

import android.app.Activity
import android.app.Instrumentation.ActivityMonitor
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.settings.AppPermissionSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PermissionSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun thirdLevelUsesRegularTitleSystemEntriesAndLocalBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity = it; wake() }
            ready()
            navigateSettings()
            assertRegularHeader("设置")
            val wide = compose.onAllNodesWithTag("navigation-shell").fetchSemanticsNodes().isNotEmpty()
            openPermissions()
            assertRegularHeader("权限管理")
            if (wide) compose.onNodeWithTag("navigation-menu").assertExists()
            capture("permissions-${Build.MODEL.replace(' ', '-')}")
            val last = AtomicReference<Intent>()
            val monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult {
                    last.set(Intent(intent))
                    return ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                compose.onNodeWithText("通知权限").performScrollTo().performClick()
                compose.waitUntil(5_000) { last.get() != null }
                assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, last.get().action)
                assertEquals(activity.packageName, last.get().getStringExtra(Settings.EXTRA_APP_PACKAGE))
                compose.onNodeWithText("位置信息").performScrollTo().performClick()
                val direct = AppPermissionSettings.intent(activity)
                assertEquals(direct?.action ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS, last.get().action)
                if (direct != null) {
                    assertEquals(activity.packageName, last.get().getStringExtra("android.intent.extra.PACKAGE_NAME"))
                } else assertEquals("package:${activity.packageName}", last.get().dataString)
                compose.onNodeWithText("后台定位").performScrollTo().performClick()
                assertEquals(direct?.action ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS, last.get().action)
                if (Build.VERSION.SDK_INT >= 36) {
                    compose.onNodeWithText("实时动态").performScrollTo().performClick()
                    assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, last.get().action)
                }
                compose.onNodeWithText("电池优化").performScrollTo().performClick()
                assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, last.get().action)
                compose.onNodeWithText("全部应用权限").performScrollTo().performClick()
                assertEquals(direct?.action ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS, last.get().action)
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            instrumentation.runOnMainSync {
                val dispatcher = activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(30f, 300f, 0.5f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackCancelled()
            }
            compose.onNodeWithTag("permissions-content").assertExists()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitForIdle()
            compose.onNodeWithTag("permissions-content").assertDoesNotExist()
            assertEquals("\"/settings\"", evaluate("location.hash.slice(1)"))
            compose.onNodeWithText("权限管理").assertExists()
        }
    }

    @Test fun systemSettingsReturnAndRecreationKeepThirdLevel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity = it; wake() }
            ready()
            navigateSettings()
            openPermissions()
            compose.onNodeWithText("全部应用权限").performScrollTo().performClick()
            compose.waitUntil(10_000) { !activity.hasWindowFocus() }
            instrumentation.uiAutomation.waitForIdle(500, 5_000)
            val back = instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(back).use { it.readBytes() }
            compose.waitUntil(10_000) { activity.hasWindowFocus() }
            compose.onNodeWithTag("permissions-content").assertExists()
            scenario.recreate()
            scenario.onActivity { activity = it; wake() }
            ready()
            compose.onNodeWithTag("permissions-content").assertExists()
            assertRegularHeader("权限管理")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("permissions-content").assertDoesNotExist()
            assertEquals("\"/settings\"", evaluate("location.hash.slice(1)"))
        }
    }

    private fun wake() {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun ready() = compose.waitUntil(20_000) {
        evaluate("Boolean(window.crticketSettings)") == "true"
    }

    private fun navigateSettings() {
        evaluate("window.crticketNavigate('/settings'); true")
        compose.waitUntil(10_000) { evaluate("location.hash.slice(1)") == "\"/settings\"" }
        compose.waitForIdle()
    }

    private fun openPermissions() {
        compose.onNodeWithText("权限管理").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("permissions-content").assertExists()
    }

    private fun assertRegularHeader(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("settings-header")))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(FontWeight.Normal, layouts.single().layoutInput.style.fontWeight)
    }

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        var value = "null"
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { value = it; done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return value
    }

    private fun capture(name: String) {
        val bitmap = Bitmap.createBitmap(
            activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888,
        )
        val done = CountDownLatch(1)
        var result = -1
        instrumentation.runOnMainSync {
            android.view.PixelCopy.request(activity.window, bitmap, {
                result = it
                done.countDown()
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, result)
        val folder = File(activity.getExternalFilesDir(null), "permission-scenes").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
