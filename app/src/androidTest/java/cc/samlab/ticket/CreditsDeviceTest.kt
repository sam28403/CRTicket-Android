package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
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
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = instrumentation.targetContext
        .getSharedPreferences("android", Context.MODE_PRIVATE)
    private var originalNotice: Boolean? = null
    private var wasInteractive = false

    @Before fun prepare() {
        wasInteractive = instrumentation.targetContext
            .getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        originalNotice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After fun restore() {
        val editor = preferences.edit()
        originalNotice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
            ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
        if (!wasInteractive) {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        }
    }

    @Test fun creditsOpenAndReturnToAbout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            awaitTag("expressive-navigation")
            compose.onNodeWithText("关于").performScrollTo().performClick()
            awaitTag("navigation-page:/about")
            compose.onNodeWithContentDescription("报告错误").assertIsDisplayed()
            compose.onNodeWithContentDescription("开源致谢").performScrollTo().performClick()
            awaitTag("credits-screen")
            compose.onNodeWithText("Kotlin").assertIsDisplayed()
            capture("cards")
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_VIEW).apply {
                addDataScheme("https")
            }
            val monitor = instrumentation.addMonitor(
                filter, android.app.Instrumentation.ActivityResult(0, null), true,
            )
            try {
                compose.onNodeWithText("Kotlin").performClick()
                compose.waitForIdle()
                org.junit.Assert.assertTrue(monitor.hits > 0)
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            compose.onNodeWithContentDescription("返回").performClick()
            awaitTag("navigation-page:/about")
            compose.onNodeWithContentDescription("开源致谢").assertIsDisplayed()
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

    private fun capture(name: String) {
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "credits-scenes")
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
