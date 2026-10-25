package cc.samlab.ticket

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.ui.welcome.WelcomeScreen
import cc.samlab.ticket.ui.home.homeColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WelcomeBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun selectedWelcomeBackupOpensRestoreSelectionAfterStartup() {
        WelcomeTestState(context).use {
            val backup = File(context.cacheDir, "welcome-backup-test.json").apply {
                writeText("[]")
            }
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            var activity: MainActivity? = null
            try {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_WELCOME_BACKUP, Uri.fromFile(backup).toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                activity = checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 15_000))
                    as MainActivity
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText(context.getString(R.string.settings_start_import))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(hasText(context.getString(R.string.backup_content_tickets)) and isToggleable())
                    .assertIsOn()
                compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
                compose.onNodeWithText(context.getString(R.string.settings_start_import)).assertDoesNotExist()
            } finally {
                instrumentation.removeMonitor(monitor)
                instrumentation.runOnMainSync { activity?.finish() }
                backup.delete()
            }
        }
    }

    @Test fun termsLeadToBackupAndOnlySkipCompletesWelcome() {
        val monitor = instrumentation.addMonitor(WelcomeActivity::class.java.name, null, false)
        context.startActivity(WelcomeActivity.releaseNotesIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000))
            as WelcomeActivity
        instrumentation.removeMonitor(monitor)
        var imported = 0
        var completed = 0
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.setContent {
                    MaterialTheme(colorScheme = homeColorScheme(context, isSystemInDarkTheme())) {
                        WelcomeScreen(
                            firstUse = true,
                            changes = "",
                            onExit = {},
                            onImportBackup = { imported++ },
                            onComplete = { completed++ },
                        )
                    }
                }
            }
            compose.onNodeWithTag("welcome-import-backup").assertDoesNotExist()
            compose.onNodeWithTag("welcome-next").performClick()
            compose.onNodeWithText(context.getString(R.string.welcome_read_all)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.welcome_terms_end)).performScrollTo()
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            if (context.resources.configuration.screenWidthDp >= 840) {
                val agree = compose.onNodeWithText(context.getString(R.string.welcome_agree))
                    .fetchSemanticsNode().boundsInRoot
                val decline = compose.onNodeWithText(context.getString(R.string.welcome_decline))
                    .fetchSemanticsNode().boundsInRoot
                assertEquals(agree.top, decline.top, 1f)
                assertTrue(decline.left > agree.right)
                val actions = compose.onNodeWithTag("welcome-terms-actions")
                    .fetchSemanticsNode().boundsInRoot
                val content = compose.onNodeWithTag("welcome-content")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(content.bottom - actions.bottom < 64 * context.resources.displayMetrics.density)
            }
            capture("welcome-terms.png")
            compose.onNodeWithText(context.getString(R.string.welcome_agree)).performClick()
            compose.onNodeWithText(context.getString(R.string.welcome_backup_message)).assertIsDisplayed()
            capture("welcome-backup.png")
            compose.onNodeWithTag("welcome-import-backup").performClick()
            compose.runOnIdle {
                assertEquals(1, imported)
                assertEquals(0, completed)
            }
            compose.onNodeWithTag("welcome-skip-backup").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, completed) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
