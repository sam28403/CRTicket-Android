package cc.samlab.ticket

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.PowerManager
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.i18n.AppLanguages
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageSettingsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    @Test
    fun pickerSwitchesLanguageSurvivesRecreationAndRestoresSystemDefault() {
        val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        val interactive = target.getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var original = LocaleListCompat.getEmptyLocaleList()
                scenario.onActivity { original = AppCompatDelegate.getApplicationLocales() }
                try {
                    scenario.onActivity { AppLanguages.select("en") }
                    awaitLanguage(scenario, "en", "Language")
                    awaitPage("/home")
                    compose.onNodeWithText("Settings").performScrollTo().performClick()
                    awaitPage("/settings")
                    compose.onNodeWithText("Language").performScrollTo().performClick()
                    awaitPage("/settings/language")
                    compose.onNodeWithTag("language-option:en").assertIsSelected()
                    capture("english")

                    choose("zh-HK")
                    awaitLanguage(scenario, "zh-HK", "語言")
                    awaitPage("/settings/language")
                    assertChoiceSelected("zh-HK")
                    scenario.recreate()
                    awaitLanguage(scenario, "zh-HK", "語言")
                    awaitPage("/settings/language")
                    assertChoiceSelected("zh-HK")
                    capture("hong-kong-after-recreation")
                    pressBack()
                    awaitPage("/settings")
                    compose.onNodeWithText("語言").performScrollTo().performClick()
                    awaitPage("/settings/language")

                    choose("ar")
                    awaitLanguage(scenario, "ar", "اللغة")
                    awaitPage("/settings/language")
                    scenario.onActivity {
                        assertEquals(View.LAYOUT_DIRECTION_RTL, it.resources.configuration.layoutDirection)
                    }
                    assertChoiceSelected("ar")
                    capture("arabic")

                    choose("system")
                    compose.waitUntil(15_000) {
                        var ready = false
                        scenario.onActivity {
                            ready = AppLanguages.selectedTag().isEmpty() &&
                                it.resources.configuration.locales[0].language ==
                                Resources.getSystem().configuration.locales[0].language
                        }
                        ready
                    }
                    awaitPage("/settings/language")
                    assertChoiceSelected("system")
                    scenario.onActivity {
                        assertEquals(
                            Resources.getSystem().configuration.locales[0].language,
                            it.resources.configuration.locales[0].language,
                        )
                    }
                    capture("system-default")
                    pressBack()
                    awaitPage("/settings")
                    pressBack()
                    awaitPage("/home")
                } finally {
                    instrumentation.runOnMainSync {
                        AppCompatDelegate.setApplicationLocales(original)
                    }
                    compose.waitUntil(15_000) {
                        AppCompatDelegate.getApplicationLocales() == original
                    }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            val editor = preferences.edit()
            notice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
            editor.commit()
            if (!interactive) {
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            }
        }
    }

    @Test
    fun pickerCatalogUsesEveryDeclaredLanguageAndDistinctNativeNames() {
        val languages = AppLanguages.supported(target)
        assertEquals(supportedTestLocales(target), languages.map { it.tag })
        assertEquals(languages.size, languages.map { it.nativeName }.toSet().size)
        assertTrue(languages.all { it.nativeName.isNotBlank() })
        assertEquals("简体中文（中国）", languages.first { it.tag == "zh-CN" }.nativeName)
        assertEquals("ئۇيغۇرچە", languages.first { it.tag == "ug" }.nativeName)
    }

    private fun awaitLanguage(scenario: ActivityScenario<MainActivity>, tag: String, title: String) {
        compose.waitUntil(15_000) {
            var ready = false
            scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
                ready = AppLanguages.selectedTag() == tag && it.getString(R.string.settings_language) == title
                if (ready) assertEquals(title, appString(R.string.settings_language))
            }
            ready
        }
    }

    private fun awaitPage(route: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("navigation-page:$route"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("navigation-page:$route").assertIsDisplayed()
    }

    private fun choose(tag: String) {
        compose.onNodeWithTag("settings-language-list")
            .performScrollToNode(hasTestTag("language-option:$tag"))
        compose.onNodeWithTag("language-option:$tag").performClick()
    }

    private fun assertChoiceSelected(tag: String) {
        compose.onNodeWithTag("settings-language-list")
            .performScrollToNode(hasTestTag("language-option:$tag"))
        compose.onNodeWithTag("language-option:$tag").assertIsSelected()
    }

    private fun capture(name: String) {
        val directory = File(target.getExternalFilesDir(null), "language-settings-scenes")
            .apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
