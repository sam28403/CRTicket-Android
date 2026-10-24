package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.welcome.WelcomeState
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueReportDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = instrumentation.targetContext
        .getSharedPreferences("android", Context.MODE_PRIVATE)
    private var originalNotice: Boolean? = null
    private var wasInteractive = false
    private val welcome = instrumentation.targetContext
        .getSharedPreferences("welcome", Context.MODE_PRIVATE)
    private var originalTerms = 0
    private var originalVersion: String? = null
    private var originalVersionCode: Int? = null

    @Before fun prepare() {
        originalTerms = welcome.getInt("terms", 0)
        originalVersion = welcome.getString("version", null)
        originalVersionCode = if (welcome.contains("versionCode")) welcome.getInt("versionCode", -1) else null
        WelcomeState.complete(instrumentation.targetContext)
        wasInteractive = instrumentation.targetContext
            .getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        originalNotice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After fun restore() {
        welcome.edit().putInt("terms", originalTerms)
            .putString("version", originalVersion)
            .apply {
                originalVersionCode?.let { putInt("versionCode", it) } ?: remove("versionCode")
            }.commit()
        val editor = preferences.edit()
        originalNotice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
            ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
        if (!wasInteractive) {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        }
    }

    @Test fun reportValidatesInputAndReturnsToAbout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            awaitTag("expressive-navigation")
            val wide = compose.onAllNodesWithTag("navigation-menu").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithText("关于").performScrollTo().performClick()
            awaitTag("navigation-page:/about")
            capture("entry")
            compose.onNodeWithContentDescription("报告错误").performScrollTo().performClick()
            awaitTag("report-screen")
            compose.onNodeWithText("前往 GitHub 提交").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("report-title").performScrollTo().performTextInput("日期显示错误")
            compose.onNodeWithTag("report-description").performScrollTo()
                .performClick()
                .performTextInput("打开车票后，日期与选择的日期不一致。")
            capture("keyboard-description")
            assertReportKeyboardViewport(compose, scenario)
            compose.onNodeWithTag("report-expected").performScrollTo().performClick()
                .performTextInput("应显示选择的日期")
            assertReportKeyboardViewport(compose, scenario)
            compose.onNodeWithTag("report-expected").assertIsDisplayed()
            capture("keyboard-expected")
            closeSoftKeyboard()
            compose.onNodeWithTag("report-device").performScrollTo().performClick()
            compose.onNodeWithText("前往 GitHub 提交").performScrollTo().assertIsEnabled()
            scenario.onActivity { activity ->
                val preview = textViews(activity.window.decorView)
                    .map { it.text.toString() }.first { it.contains("GMS:") }
                assertTrue(preview.contains("\nGMS: "))
                assertTrue(preview.contains("\nWebView: "))
            }
            capture("preview")
            compose.onNodeWithTag("report-title").performScrollTo()
            capture("form")
            compose.onNodeWithContentDescription("返回").performClick()
            awaitTag("navigation-page:/about")
            compose.onNodeWithContentDescription("GitHub").assertIsDisplayed()
            if (!wide) {
                pressBack()
                awaitTag("navigation-page:/home")
            }
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) {
            runCatching {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun capture(name: String) {
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "issue-report-scenes")
            .apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
