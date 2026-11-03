package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.SettingsPage
import cc.samlab.ticket.ui.settings.SettingsUiState
import cc.samlab.ticket.ui.settings.matchesDeveloperSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperOptionsTest {
    @Test fun searchRequiresExactInput() {
        listOf("G8964", "CR400AF-T-2808").forEach { query ->
            assertTrue(matchesDeveloperSearch(query))
            assertFalse(matchesDeveloperSearch(query.lowercase()))
            assertFalse(matchesDeveloperSearch(" $query"))
            assertFalse(matchesDeveloperSearch("$query "))
            assertFalse(matchesDeveloperSearch(query.dropLast(1)))
            assertFalse(matchesDeveloperSearch(query + "0"))
        }
        assertFalse(matchesDeveloperSearch(""))
        assertFalse(matchesDeveloperSearch("开发者选项"))
    }

    @Test fun developerPageReturnsToSettings() {
        val state = SettingsUiState()
        state.open("theme")
        state.openDeveloper()
        assertEquals(SettingsPage.DEVELOPER, state.page)
        assertEquals("", state.dialog)
        assertTrue(state.closeDetail())
        assertEquals(SettingsPage.MAIN, state.page)
    }
}
