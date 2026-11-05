package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.developer
import cc.samlab.ticket.ui.settings.timetable
import cc.samlab.ticket.ui.settings.debugTickets

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.settings.NativeSettingsController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperSearchPrivacyDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun onlyExactPassphrasesExposeDeveloperSettingsAfterUnlocking() {
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val preferences = context.getSharedPreferences("developer-options", 0)
        val originalVisible = preferences.getBoolean("visible", false)
        val originalAuto = preferences.getBoolean("lltAutoUpdate", true)
        val originalNetwork = NetworkFeatures.isAllowed()
        val delegate = MainActivity::class.java.getDeclaredField("settings\$delegate").apply {
            isAccessible = true
        }
        val settings = (delegate.get(context) as Lazy<*>).value as NativeSettingsController
        try {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, false)
                settings.developer.reveal()
            }
            fixture.evaluate("window.dispatchEvent(new Event('crticket-network-changed'))")
            fixture.navigate("/search")
            val hiddenTitles = listOf(
                R.string.settings_developer, R.string.debug_tickets_title,
                R.string.failure_debug, R.string.failure_test_main, R.string.failure_test_worker,
                R.string.failure_test_anr, R.string.network_features_enabled,
                R.string.llt_auto_update, R.string.llt_import_title,
                R.string.developer_show_non_running_trains,
            ).map(context::getString)
            val field = compose.onNodeWithTag("global-search-field")
            for (query in hiddenTitles + listOf("开发者", "调试", "developer", "debug",
                "G896", "g8964", "G8964 ", "CR400AF-T-280")) {
                field.performTextReplacement(query)
                compose.waitForIdle()
                hiddenTitles.forEach { title ->
                    compose.onAllNodes(hasText(title) and !hasSetTextAction()).assertCountEquals(0)
                }
            }
            for (passphrase in listOf("G8964", "CR400AF-T-2808")) {
                field.performTextReplacement(passphrase)
                compose.onNodeWithTag("global-search-results").performScrollToNode(
                    hasText(context.getString(R.string.settings_developer)),
                )
                compose.onNode(hasText(context.getString(R.string.settings_developer)) and hasClickAction())
                    .assertIsDisplayed()
            }
            fixture.capture("developer-search-passphrase-only")
        } finally {
            fixture.instrumentation.runOnMainSync {
                NetworkFeatures.setEnabled(context, originalNetwork)
                preferences.edit().putBoolean("visible", originalVisible)
                    .putBoolean("lltAutoUpdate", originalAuto).commit()
            }
            fixture.close()
        }
    }
}
