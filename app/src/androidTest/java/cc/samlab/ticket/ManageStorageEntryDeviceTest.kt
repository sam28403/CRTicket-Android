package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.settings.ManageStorageActivity
import cc.samlab.ticket.ui.welcome.WelcomeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ManageStorageEntryDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun coldLaunchShowsStorageWithoutShowingSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WelcomeState.complete(context)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(ManageStorageActivity.OPEN_STORAGE, true)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(20_000) {
                compose.onNodeWithTag("settings-content").assertDoesNotExist()
                compose.onAllNodesWithTag("storage-category:ERROR_LOGS").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("settings-storage").assertIsDisplayed()
            compose.onNodeWithTag("settings-content").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.settings_operation_failed)).assertDoesNotExist()
        }
    }

    @Test fun systemManageSpaceEntryOpensStorageWithoutDeletingData() {
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val saved = File(context.filesDir, "manage-space-device-test.keep")
        try {
            saved.writeText("retained")
            fixture.evaluate("localStorage.setItem('manage-space-test', 'retained')")
            val info = context.packageManager.getApplicationInfo(context.packageName, 0)
            assertEquals("cc.samlab.ticket.ui.settings.ManageStorageActivity", info.manageSpaceActivityName)
            fixture.shell("am start -n ${context.packageName}/${info.manageSpaceActivityName}")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("settings-storage").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("settings-storage").assertIsDisplayed()
            assertEquals("retained", saved.readText())
            assertEquals("\"retained\"", fixture.evaluate("localStorage.getItem('manage-space-test')"))
            fixture.capture("system-manage-space-entry")
        } finally {
            saved.delete()
            fixture.evaluate("localStorage.removeItem('manage-space-test')")
            fixture.close()
        }
    }
}
