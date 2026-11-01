package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.help.helpTopics
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchCatalogueTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun settingsAndAllHelpTopicsAreSearchable() {
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/search")
            val field = compose.onNodeWithTag("global-search-field")
            val results = compose.onNodeWithTag("global-search-results")
            for (title in listOf(R.string.settings_map, R.string.settings_storage,
                R.string.update_previews, R.string.permissions_exact_alarms)) {
                field.performTextReplacement(fixture.activity.getString(title))
                field.performImeAction()
                results.performScrollToNode(hasTestTag("search-settings-title"))
                compose.onNodeWithTag("search-settings-title").assertIsDisplayed()
            }
            for (topic in helpTopics) {
                field.performTextReplacement(fixture.activity.getString(topic.title))
                field.performImeAction()
                results.performScrollToNode(hasTestTag("search-help-title"))
                compose.onNodeWithTag("search-help-title").assertIsDisplayed()
            }
            compose.onNodeWithTag("global-vehicle-search").assertDoesNotExist()
            fixture.capture("global-settings-help")
        } finally {
            fixture.close()
        }
    }
}
