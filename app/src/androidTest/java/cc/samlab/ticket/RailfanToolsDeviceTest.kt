package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RailfanToolsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)

    @Test
    fun toolsDefaultToHiddenAndPersistUntilDisabled() {
        val keys = listOf("railfanToolsEnabled", IMPORTANT_NOTICE_SUPPRESSED)
        val previous = keys.associateWith { key ->
            if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        }
        preferences.edit()
            .remove("railfanToolsEnabled")
            .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true)
            .commit()
        val tools = listOf(
            R.string.quiz_title to "/railfan/quiz",
            R.string.railfan_vehicle to "/railfan/vehicle",
            R.string.railfan_routing to "/railfan/routing",
            R.string.railfan_lines to "/railfan/lines",
            R.string.railfan_visits to "/railfan/visits",
            R.string.fare_title to "/price",
            R.string.railfan_speedometer to "/railfan/speedometer",
        )
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var text = target.resources
                scenario.onActivity {
                    text = it.resources
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                }
                awaitPage("/home")
                assertFalse(RailfanPreferences.enabled(target))
                tools.forEach { (title, _) ->
                    compose.onNodeWithText(text.getString(title)).assertDoesNotExist()
                }
                compose.onNodeWithText(text.getString(R.string.home_settings))
                    .performScrollTo().performClick()
                awaitPage("/settings")
                compose.onNodeWithText(text.getString(R.string.settings_advanced))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(text.getString(R.string.railfan_tools))
                    .performScrollTo().performClick()
                compose.waitUntil { RailfanPreferences.enabled(target) }
                compose.onNodeWithContentDescription(text.getString(R.string.railfan_tools))
                    .assertIsOn()
                capture("advanced-enabled")
                pressBack()
                awaitPage("/home")
                compose.onNodeWithText(text.getString(R.string.railfan_tools)).assertDoesNotExist()
                tools.forEach { (title, route) ->
                    compose.onNodeWithText(text.getString(title)).performScrollTo().performClick()
                    awaitPage(route)
                    compose.onNodeWithText(text.getString(title)).assertIsDisplayed()
                    if (route == "/railfan/vehicle") capture("vehicle-placeholder")
                    pressBack()
                    awaitPage("/home")
                }
                compose.onNodeWithText(text.getString(R.string.railfan_vehicle))
                    .performScrollTo().assertIsDisplayed()
                capture("home-tools")
                scenario.recreate()
                awaitPage("/home")
                assertTrue(RailfanPreferences.enabled(target))
                compose.onNodeWithText(text.getString(R.string.railfan_speedometer))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(text.getString(R.string.home_settings))
                    .performScrollTo().performClick()
                awaitPage("/settings")
                compose.onNodeWithText(text.getString(R.string.railfan_tools))
                    .performScrollTo().performClick()
                compose.waitUntil { !RailfanPreferences.enabled(target) }
                pressBack()
                awaitPage("/home")
                tools.forEach { (title, _) ->
                    compose.onNodeWithText(text.getString(title)).assertDoesNotExist()
                }
            }
        } finally {
            val editor = preferences.edit()
            previous.forEach { (key, value) ->
                if (value == null) editor.remove(key) else editor.putBoolean(key, value)
            }
            editor.commit()
        }
    }

    private fun awaitPage(route: String) {
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag("navigation-page:$route").assertIsDisplayed()
            }.isSuccess
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(target.getExternalFilesDir(null), "railfan-scenes").apply { mkdirs() }
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
