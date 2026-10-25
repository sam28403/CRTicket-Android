package cc.samlab.ticket

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.settings.SettingsUpdates
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.ui.welcome.WelcomeScreen
import cc.samlab.ticket.update.ReleaseRepository
import cc.samlab.ticket.update.ReleaseVersion
import cc.samlab.ticket.update.updates
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun channelsIgnoreDraftsOlderVersionsAndUnsignedPackages() {
        fun release(tag: String, preview: Boolean = false, draft: Boolean = false) = JSONObject()
            .put("tag_name", tag).put("prerelease", preview).put("draft", draft)
            .put("body", "Release notes")
            .put("assets", org.json.JSONArray().put(JSONObject()
                .put("name", "CRTicket.apk").put("size", 100).put("state", "uploaded")
                .put("browser_download_url",
                    "https://github.com/sam28403/CRTicket-Android/releases/download/$tag/CRTicket.apk")))
        val repository = ReleaseRepository()
        val current = requireNotNull(ReleaseVersion.parse("1.0.0-abcdef1-261025"))
        val releases = listOf(
            release("v1.0.0"), release("v1.1.0"),
            release("v1.2.0-beta.2", preview = true), release("v2.0.0", draft = true),
        )
        assertEquals("v1.1.0", repository.selectRelease(releases, false, current)?.tag)
        val preview = repository.selectRelease(releases, true, current)
        assertEquals("v1.2.0-beta.2", preview?.tag)
        assertNotNull(preview?.downloadUrl)
        assertNull(repository.selectRelease(listOf(release("v1.0.0")), true, current))
        val unsigned = release("v1.1.0")
        unsigned.getJSONArray("assets").getJSONObject(0).put("name", "app-unsigned.apk")
        assertNull(repository.selectRelease(listOf(unsigned), false, current)?.downloadUrl)
    }

    @Test fun updateNotesUseDownloadAndLaterActions() {
        val activity = launchWelcome()
        var downloaded = false
        var dismissed = false
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        WelcomeScreen(
                            firstUse = false,
                            changes = "# v1.2.0\n\nUpdate notes",
                            onExit = { dismissed = true },
                            releaseNotesOnly = true,
                            onDownload = { downloaded = true },
                            onComplete = { dismissed = true },
                        )
                    }
                }
            }
            compose.waitForIdle()
            screenshot("update-notes.png")
            val downloadBounds = compose.onNodeWithText(context.getString(R.string.update_download))
                .fetchSemanticsNode().boundsInRoot
            val laterBounds = compose.onNodeWithText(context.getString(R.string.update_later))
                .fetchSemanticsNode().boundsInRoot
            if (context.resources.configuration.screenWidthDp >= 840) {
                assertEquals(downloadBounds.top, laterBounds.top, 1f)
            } else {
                val gap = laterBounds.top - downloadBounds.bottom
                assertTrue(
                    "Visible button gap: $gap px",
                    gap > 0 && gap <= 20 * context.resources.displayMetrics.density,
                )
            }
            compose.onNodeWithText(context.getString(R.string.update_download)).assertIsDisplayed().performClick()
            compose.onNodeWithText(context.getString(R.string.update_later)).assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertTrue(downloaded)
                assertTrue(dismissed)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun updatesAreReachableFromTheMainSettingsPage() {
        val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val automatic = preferences.getBoolean("automatic", true)
        preferences.edit().putBoolean("automatic", false).commit()
        WelcomeTestState(context).use {
            val activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as MainActivity
            try {
                instrumentation.runOnMainSync {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                }
                compose.waitUntil(20_000) {
                    var ready = false
                    val latch = java.util.concurrent.CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        activity.webView.evaluateJavascript("Boolean(window.crticketNavigate)") {
                            ready = it == "true"
                            latch.countDown()
                        }
                    }
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS) && ready
                }
                instrumentation.runOnMainSync {
                    activity.webView.evaluateJavascript("window.crticketNavigate('/settings')", null)
                }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(context.getString(R.string.update_check))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                if (compose.onAllNodesWithTag("important-notice-confirm")
                        .fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("important-notice-confirm").performClick()
                }
                compose.onNodeWithText(context.getString(R.string.update_check))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.update_previews))
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.update_automatic))
                    .performScrollTo().assertIsDisplayed()
                screenshot("update-settings.png")
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                preferences.edit().putBoolean("automatic", automatic).commit()
            }
        }
    }

    @Test fun updateSwitchesDefaultAndPersist() {
        org.junit.Assume.assumeTrue(BuildConfig.UPDATE_ENABLED)
        val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val original = preferences.all
        preferences.edit().clear().commit()
        val activity = launchWelcome()
        try {
            instrumentation.runOnMainSync {
                val model = activity.updates()
                assertFalse(model.previews)
                assertTrue(model.automatic)
                activity.setContent {
                    MaterialTheme { Column { SettingsUpdates() } }
                }
            }
            compose.onNodeWithText(context.getString(R.string.update_previews)).performClick()
            compose.onNodeWithText(context.getString(R.string.update_automatic)).performClick()
            compose.runOnIdle {
                assertTrue(activity.updates().previews)
                assertFalse(activity.updates().automatic)
                assertTrue(preferences.getBoolean("previews", false))
                assertFalse(preferences.getBoolean("automatic", true))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            preferences.edit().clear().apply {
                original.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                    }
                }
            }.commit()
        }
    }

    @Test fun ciBuildDisablesBothSettingsAndBackgroundUpdates() {
        org.junit.Assume.assumeFalse(BuildConfig.UPDATE_ENABLED)
        val activity = launchWelcome()
        try {
            instrumentation.runOnMainSync {
                val model = activity.updates()
                model.onStartup()
                model.check()
                model.changeAutomatic(true)
                model.changePreviews(true)
                model.download()
                assertFalse(model.checking)
                assertFalse(model.automatic)
                assertFalse(model.previews)
                assertNull(model.release)
                activity.setContent { MaterialTheme { Column { SettingsUpdates() } } }
            }
            compose.onNodeWithText(context.getString(R.string.update_check)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.update_previews)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.update_automatic)).assertIsNotEnabled()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun screenshot(name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun launchWelcome(): WelcomeActivity {
        val activity = instrumentation.startActivitySync(
            WelcomeActivity.releaseNotesIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as WelcomeActivity
        instrumentation.runOnMainSync {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        return activity
    }
}
