package cc.samlab.ticket

import cc.samlab.ticket.permissions.SettingsReturn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReturnTest {
    @Test fun earlySettingsResultCannotFinishBeforeAnActualReturn() {
        val state = SettingsReturn()
        state.launch()
        assertFalse(state.onResume())
        state.onPause()
        assertTrue(state.onResume())
        assertFalse(state.onResume())
    }

    @Test fun permissionDialogResumeDoesNotCountAsSettingsReturn() {
        val state = SettingsReturn()
        state.onPause()
        assertFalse(state.onResume())
        state.launch()
        assertFalse(state.onResume())
    }

    @Test fun cancellationIgnoresLateReturnAndNextLaunchStartsFresh() {
        val state = SettingsReturn()
        state.launch()
        state.onPause()
        state.cancel()
        assertFalse(state.onResume())
        state.launch()
        assertFalse(state.onResume())
        state.onPause()
        assertTrue(state.onResume())
    }
}
