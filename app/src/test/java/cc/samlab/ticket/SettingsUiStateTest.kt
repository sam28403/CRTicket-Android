package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.SettingsPage
import cc.samlab.ticket.ui.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiStateTest {
    @Test fun languageIsAnIndependentSettingsDetail() {
        val state = SettingsUiState()
        state.open("theme")
        state.openLanguage()
        assertEquals(SettingsPage.LANGUAGE, state.page)
        assertEquals("", state.dialog)
        assertTrue(state.closeDetail())
        assertEquals(SettingsPage.MAIN, state.page)
        assertFalse(state.closeDetail())
    }

    @Test fun arrivalReturnsToSettingsBeforeLeavingTheSettingsRoute() {
        val state = SettingsUiState()
        state.openArrival()
        assertEquals(SettingsPage.ARRIVAL, state.page)
        assertTrue(state.hasDetail)
        state.backProgress = 0.7f
        assertTrue(state.closeDetail())
        assertEquals(SettingsPage.MAIN, state.page)
        assertEquals(0f, state.backProgress, 0f)
        assertFalse(state.closeDetail())
    }

    @Test fun permissionShortcutReturnsToArrivalAndThenSettings() {
        val state = SettingsUiState()
        state.openArrival()
        state.openPermissions()
        assertEquals(SettingsPage.PERMISSIONS, state.page)
        state.closeDetail()
        assertEquals(SettingsPage.ARRIVAL, state.page)
        state.closeDetail()
        assertEquals(SettingsPage.MAIN, state.page)
        state.openPermissions()
        state.closeDetail()
        assertEquals(SettingsPage.MAIN, state.page)
    }

    @Test fun leavingSettingsClearsNestedPagesAndAnyOpenDialog() {
        val state = SettingsUiState()
        state.open("name")
        state.nameDraft = "临时名称"
        state.openArrival()
        assertEquals("", state.dialog)
        assertEquals(null, state.nameDraft)
        state.openPermissions()
        state.resetPage()
        assertFalse(state.hasDetail)
        state.openPermissions()
        state.closeDetail()
        assertEquals(SettingsPage.MAIN, state.page)
    }
}
