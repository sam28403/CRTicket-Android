package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperOptionsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun developerNavigationSearchPersistenceAndBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("developer-options", Context.MODE_PRIVATE)
        val hadValue = preferences.contains("visible")
        val original = preferences.getBoolean("visible", false)
        preferences.edit().putBoolean("visible", false).commit()
        var fixture: TabletPageFixture? = null
        try {
            val first = TabletPageFixture(compose)
            fixture = first
            val title = context.getString(R.string.settings_developer)
            val debugTitle = context.getString(R.string.failure_test_main)
            first.navigate("/settings")
            compose.onAllNodes(hasText(title) and !hasSetTextAction()).assertCountEquals(0)
            compose.onAllNodesWithText(debugTitle).assertCountEquals(0)
            val backup = BackupPreferences(context)
            assertFalse(backup.snapshot().toString().contains("developer"))
            backup.restore(JSONObject().put("visible", true).put("developerOptionsVisible", true))
            backup.complete()
            assertFalse(preferences.getBoolean("visible", false))
            first.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            for (query in listOf("G896", "g8964", "G8964 ", "CR400AF-T-280", title, debugTitle)) {
                field.performTextReplacement(query)
                compose.waitForIdle()
                compose.onAllNodes(hasText(title) and !hasSetTextAction()).assertCountEquals(0)
            }
            for (query in listOf("G8964", "CR400AF-T-2808")) {
                field.performTextReplacement(query)
                field.performImeAction()
                compose.onNodeWithTag("global-search-results")
                    .performScrollToNode(hasText(title))
                compose.onNode(hasText(title) and hasClickAction()).assertIsDisplayed()
                assertFalse(preferences.getBoolean("visible", false))
            }
            first.capture("developer-search")
            compose.onNode(hasText(title) and hasClickAction()).performClick()
            compose.onNodeWithTag("settings-developer").assertIsDisplayed()
            compose.onNodeWithText(debugTitle).assertIsDisplayed()
            compose.waitUntil(5_000) { preferences.getBoolean("visible", false) }
            first.capture("developer-options")
            pressBack()
            compose.onNodeWithTag("global-search-screen").assertIsDisplayed()
            first.navigate("/settings")
            compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithTag("settings-developer").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("settings-content").assertExists()
            assertFalse(backup.snapshot().toString().contains("developer"))
            backup.restore(JSONObject().put("visible", false).put("developerOptionsVisible", false))
            backup.complete()
            assertTrue(preferences.getBoolean("visible", false))
            first.close()
            fixture = null
            val second = TabletPageFixture(compose)
            fixture = second
            second.navigate("/settings")
            compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithTag("settings-developer").assertIsDisplayed()
        } finally {
            fixture?.close()
            val editor = preferences.edit()
            if (hadValue) editor.putBoolean("visible", original) else editor.remove("visible")
            editor.commit()
        }
    }
}
