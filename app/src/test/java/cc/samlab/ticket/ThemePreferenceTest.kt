package cc.samlab.ticket

import cc.samlab.ticket.ui.ThemePreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {
    @Test fun systemPreferenceResolvesAgainAfterSystemModeChanges() {
        for (preference in listOf(null, "classic-system", "contrast-system")) {
            assertFalse(ThemePreference.resolveDark(preference, false))
            assertTrue(ThemePreference.resolveDark(preference, true))
            assertFalse(ThemePreference.resolveDark(preference, false))
        }
    }

    @Test fun explicitPreferenceDoesNotFollowSystemChanges() {
        for (systemDark in listOf(false, true)) {
            for (palette in listOf("classic", "contrast")) {
                assertFalse(ThemePreference.resolveDark("$palette-light", systemDark))
                assertTrue(ThemePreference.resolveDark("$palette-dark", systemDark))
            }
        }
    }
}
