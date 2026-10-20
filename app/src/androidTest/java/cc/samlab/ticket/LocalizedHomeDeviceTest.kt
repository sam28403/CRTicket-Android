package cc.samlab.ticket

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.home.ExpressiveHomeScreen
import cc.samlab.ticket.ui.home.HomeNavigationState
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedHomeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)
    private var notice: Boolean? = null
    private var interactive = false

    @Before
    fun prepare() {
        interactive = target.getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        notice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After
    fun restore() {
        val editor = preferences.edit()
        notice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
            ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
        if (!interactive) {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        }
    }

    @Test
    fun homeRecomposesForAllSupportedLanguages() {
        val language = mutableStateOf("en")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                val view = activity.findViewById<ComposeView>(R.id.expressive_navigation_view)
                view.setContent {
                    val tag by language
                    val configuration = remember(tag) {
                        Configuration(activity.resources.configuration).apply {
                            setLocales(LocaleList.forLanguageTags(tag))
                        }
                    }
                    val context = remember(configuration) {
                        activity.createConfigurationContext(configuration)
                    }
                    CompositionLocalProvider(
                        LocalContext provides context,
                        LocalConfiguration provides configuration,
                        LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            LayoutDirection.Rtl
                        } else {
                            LayoutDirection.Ltr
                        },
                    ) {
                        ExpressiveHomeScreen(
                            dark = false,
                            visible = true,
                            onNavigate = {},
                            pureBlack = false,
                            navigation = remember(tag) { HomeNavigationState() },
                        )
                    }
                }
            }
            for (tag in supportedTestLocales(target)) {
                compose.runOnIdle { language.value = tag }
                compose.waitForIdle()
                val context = target.createConfigurationContext(
                    Configuration(target.resources.configuration).apply {
                        setLocales(LocaleList.forLanguageTags(tag))
                    },
                )
                compose.onNodeWithText(context.getString(R.string.home_ticket)).assertIsDisplayed()
                capture("$tag-home")
                compose.onNodeWithText(context.getString(R.string.home_settings))
                    .performScrollTo().assertIsDisplayed()
                capture("$tag-settings-entry")
            }
        }
    }

    private fun capture(name: String) {
        val folder = File(target.getExternalFilesDir(null), "localized-home-scenes")
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
