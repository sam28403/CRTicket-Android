package cc.samlab.ticket

import android.content.Context
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.appVersionLabel
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.ui.welcome.WelcomeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeVersionDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)

    @Test fun versionNumbersIncludingDowngradesAndLegacyRecordsRequireWelcome() {
        WelcomeTestState(context).use {
            assertFalse(WelcomeState.required(context))
            preferences.edit().putString("version", "different-semantic-version").commit()
            assertFalse(WelcomeState.required(context))
            for (code in listOf(BuildConfig.VERSION_CODE - 1, BuildConfig.VERSION_CODE + 1)) {
                preferences.edit().putInt("versionCode", code).commit()
                assertTrue(WelcomeState.required(context))
                assertFalse(WelcomeState.needsTerms(context))
            }
            preferences.edit().remove("versionCode").putString("version", WelcomeState.version).commit()
            assertTrue(WelcomeState.required(context))
            WelcomeState.complete(context)
            assertFalse(WelcomeState.required(context))
            preferences.edit().putInt("terms", 0).commit()
            assertTrue(WelcomeState.needsTerms(context))
            assertTrue(WelcomeState.required(context))
        }
    }

    @Test fun openingChangedBuildShowsWelcomeThenAboutIncludesVersionNumber() {
        WelcomeTestState(context).use {
            preferences.edit().putInt("versionCode", BuildConfig.VERSION_CODE - 1).commit()
            val welcomeMonitor = instrumentation.addMonitor(WelcomeActivity::class.java.name, null, false)
            val mainMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            var main: MainActivity? = null
            try {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                val welcome = instrumentation.waitForMonitorWithTimeout(welcomeMonitor, 15_000)
                assertNotNull("Changed version did not open WelcomeActivity", welcome)
                instrumentation.runOnMainSync {
                    welcome.setShowWhenLocked(true)
                    welcome.setTurnScreenOn(true)
                }
                val version = context.getString(R.string.welcome_version, appVersionLabel)
                compose.onNodeWithText(version).assertExists()
                assertTrue(WelcomeState.required(context))
                compose.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
                compose.onNodeWithText(context.getString(R.string.welcome_changes_title)).assertExists()
                instrumentation.removeMonitor(mainMonitor)
                val completedMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
                try {
                    compose.onNodeWithText(context.getString(R.string.welcome_start)).performClick()
                    main = instrumentation.waitForMonitorWithTimeout(completedMonitor, 15_000) as? MainActivity
                    assertNotNull(main)
                    assertFalse(WelcomeState.required(context))
                    val activity = checkNotNull(main)
                    compose.waitUntil(20_000) {
                        var ready = false
                        val latch = CountDownLatch(1)
                        instrumentation.runOnMainSync {
                            activity.webView.evaluateJavascript("Boolean(window.crticketNavigate)") {
                                ready = it == "true"
                                latch.countDown()
                            }
                        }
                        latch.await(5, TimeUnit.SECONDS) && ready
                    }
                    instrumentation.runOnMainSync {
                        activity.webView.evaluateJavascript("window.crticketNavigate('/about')", null)
                    }
                    val aboutVersion = context.getString(R.string.about_version, appVersionLabel)
                    compose.waitUntil(15_000) {
                        runCatching {
                            compose.onAllNodesWithText(aboutVersion).fetchSemanticsNodes().isNotEmpty()
                        }.getOrDefault(false)
                    }
                } finally {
                    instrumentation.removeMonitor(completedMonitor)
                }
            } finally {
                instrumentation.removeMonitor(welcomeMonitor)
                instrumentation.removeMonitor(mainMonitor)
                instrumentation.runOnMainSync { main?.finish() }
            }
        }
    }
}
