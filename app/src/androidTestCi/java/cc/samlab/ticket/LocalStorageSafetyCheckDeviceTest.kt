package cc.samlab.ticket

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageSafetyCheckDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun previousSettingsTestDidNotLeaveItsTemporaryName() {
        val fixture = TabletPageFixture(compose)
        try {
            assertEquals(
                "false",
                fixture.evaluate(
                    "JSON.parse(localStorage.getItem('user') || '{}').username === '设置联通验证'",
                ),
            )
        } finally {
            fixture.close()
        }
    }
}
