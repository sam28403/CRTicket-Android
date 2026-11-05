package cc.samlab.ticket

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicBuildIsolationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun publicBuildHasNoDeveloperWebInterfaces() {
        assumeFalse(BuildConfig.CI_BUILD)
        val fixture = TabletPageFixture(compose)
        try {
            assertEquals("cc.samlab.ticket", fixture.activity.packageName)
            assertEquals("undefined", fixture.evaluate("typeof CRTicketDeveloper").trim('"'))
            assertEquals("undefined", fixture.evaluate("typeof window.crticketTimetable").trim('"'))
            assertEquals("undefined", fixture.evaluate("typeof window.crticketDebugGenerate").trim('"'))
            assertEquals("function", fixture.evaluate("typeof window.crticketTimetableVersion").trim('"'))
        } finally {
            fixture.close()
        }
    }
}
