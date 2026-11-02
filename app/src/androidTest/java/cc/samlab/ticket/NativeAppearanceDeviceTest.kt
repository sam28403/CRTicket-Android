package cc.samlab.ticket

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.diagnostics.ErrorReportActivity
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAppearanceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences("android", 0)

    @Test fun settingsStayIndependentWhileWebRequestIsPending() {
        WelcomeTestState(context).use {
            prefs.edit().putString("themePreference", "classic-light").commit()
            val fixture = TabletPageFixture(compose)
            try {
                fixture.navigate("/settings")
                val controller = controller(fixture.activity)
                compose.waitUntil(20_000) { controller.state.loaded && !controller.busy }
                fixture.evaluate("window.crticketSettings = () => {}; true")
                instrumentation.runOnMainSync { controller.request("snapshot") }
                assertTrue(controller.busy)
                compose.onNodeWithText(context.getString(R.string.settings_display_name))
                    .performScrollTo().performClick()
                compose.onNode(hasSetTextAction()).performTextClearance()
                compose.onNode(hasSetTextAction()).performTextInput("原生设置验证")
                compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
                assertEquals("原生设置验证", prefs.getString("profile_username", ""))
                compose.onNodeWithText(context.getString(R.string.settings_theme))
                    .performScrollTo().performClick()
                compose.onNode(hasText(context.getString(R.string.theme_dark)) and isSelectable()).performClick()
                compose.waitUntil(10_000) { prefs.getString("themePreference", "") == "classic-dark" }
                compose.onNodeWithContentDescription(context.getString(R.string.settings_pure_black_switch))
                    .performScrollTo().performClick()
                compose.waitUntil(10_000) { prefs.getString("themePreference", "") == "contrast-dark" }
                instrumentation.runOnMainSync {
                    controller.request("arrival", JSONObject().put("enabled", false))
                }
                assertFalse(controller.state.arrivalEnabled)
                assertTrue("Native actions must not complete or cancel the pending Web request", controller.busy)
                assertEquals("true", fixture.evaluate(
                    "JSON.parse(localStorage.getItem('crticket-device-owner-v1')).username === '原生设置验证'"))
                capture("settings-pending-web", true)
            } finally {
                fixture.close()
            }
        }
    }

    @Test fun standalonePagesRespectManualLightDarkAndPureBlack() {
        WelcomeTestState(context).use {
            for ((preference, dark) in listOf(
                "classic-dark" to true, "contrast-dark" to true, "classic-light" to false,
            )) {
                prefs.edit().putString("themePreference", preference).commit()
                for (page in listOf("welcome", "changes", "error")) {
                    for (command in listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
                        shell(command)
                    }
                    val intent = when (page) {
                        "changes" -> WelcomeActivity.releaseNotesIntent(context)
                        "error" -> ErrorReportActivity.intent(context)
                        else -> Intent(context, WelcomeActivity::class.java)
                    }
                    if (page == "error") {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        Thread.sleep(2000)
                        val windows = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                            .use { descriptor ->
                                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                                    .bufferedReader().use { it.readText() }
                            }
                        assertTrue("Error report must be foreground", Regex(
                            "topResumedActivity=.*ErrorReportActivity").containsMatchIn(windows))
                        capture("$page-$preference", dark)
                        shell("input keyevent KEYCODE_BACK")
                        compose.waitUntil(10_000) {
                            !Regex("topResumedActivity=.*ErrorReportActivity")
                                .containsMatchIn(shell("dumpsys activity activities"))
                        }
                        continue
                    }
                    val activity = instrumentation.startActivitySync(
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    try {
                        instrumentation.runOnMainSync {
                            activity.setShowWhenLocked(true)
                            activity.setTurnScreenOn(true)
                            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        compose.waitUntil(15_000) {
                            var focused = false
                            instrumentation.runOnMainSync { focused = activity.window.decorView.hasWindowFocus() }
                            focused
                        }
                        compose.waitForIdle()
                        capture("$page-$preference", dark)
                    } finally {
                        instrumentation.runOnMainSync { activity.finish() }
                    }
                }
            }
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
        }

    private fun controller(activity: MainActivity): NativeSettingsController {
        val field = MainActivity::class.java.getDeclaredField("settings\$delegate")
        field.isAccessible = true
        return (field.get(activity) as Lazy<*>).value as NativeSettingsController
    }

    private fun capture(name: String, dark: Boolean) {
        instrumentation.waitForIdleSync()
        Thread.sleep(700)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(context.getExternalFilesDir(null), "native-appearance-tests").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        var darkPixels = 0
        var total = 0
        for (x in 1..19) for (y in 2..18) {
            val pixel = image.getPixel(image.width * x / 20, image.height * y / 20)
            if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < 384) darkPixels++
            total++
        }
        image.recycle()
        val fraction = darkPixels.toFloat() / total
        if (dark) assertTrue("$name dark coverage: $fraction", fraction > 0.65f)
        else assertTrue("$name unexpected dark coverage: $fraction", fraction < 0.35f)
    }
}
