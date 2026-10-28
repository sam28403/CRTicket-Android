package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTimetableDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun timetableCleanupDeletesOnlyTheSelectedIndexedDbArchive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val suppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val fixture = TabletPageFixture(compose)
        fun action(value: String): String {
            fixture.evaluate("window.storageTimetableFixture('$value')")
            compose.waitUntil(10_000) {
                fixture.evaluate("window.storageTimetableResult") != "\"pending\""
            }
            return fixture.evaluate("window.storageTimetableResult")
        }
        try {
            val script = instrumentation.context.assets.open("storage-timetable-fixture.js")
                .bufferedReader().use { it.readText() }
            fixture.evaluate(script)
            assertEquals("\"done\"", action("seed"))
            fixture.evaluate("localStorage.setItem('storage-timetable-test', 'retained')")
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            val database = compose.onNode(
                hasText(context.getString(R.string.storage_timetable)) and hasClickAction(),
            )
            compose.waitUntil(10_000) {
                database.fetchSemanticsNode().config.let {
                    !it.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                }
            }
            fixture.capture("storage-timetable-overview")
            database.performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.storage_timetable_confirm))
                .assertIsDisplayed()
            fixture.capture("storage-timetable-confirmation")
            pressBack()
            assertEquals("\"exists\"", action("exists"))
            database.performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("storage-result").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("storage-result").performScrollTo().assertIsDisplayed()
            assertEquals("\"empty\"", action("exists"))
            assertEquals("\"retained\"", fixture.evaluate("localStorage.getItem('storage-timetable-test')"))
            fixture.capture("storage-timetable-cleared")
        } finally {
            action("restore")
            fixture.evaluate("localStorage.removeItem('storage-timetable-test')")
            fixture.close()
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, suppressed).commit()
            welcome.close()
        }
    }
}
