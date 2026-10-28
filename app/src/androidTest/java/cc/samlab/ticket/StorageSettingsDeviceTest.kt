package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageSettingsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cleanupKeepsSavedDataAndSupportsCancelAndBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", android.content.Context.MODE_PRIVATE)
        val suppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val line = File(context.cacheDir, "railway-lines/storage-device-test.tmp")
        val share = File(context.cacheDir, "quiz-share/storage-device-test.png")
        val export = File(context.cacheDir, "export-storage-device-test.tmp")
        val saved = File(context.filesDir, "storage-device-test.keep")
        val unrelated = File(context.cacheDir, "storage-device-test.keep")
        listOf(line, share, export, saved, unrelated).forEach {
            it.parentFile!!.mkdirs()
            it.writeBytes(ByteArray(8192) { 42 })
        }
        val fixture = TabletPageFixture(compose)
        try {
            fixture.evaluate("localStorage.setItem('storage-device-test', 'retained')")
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("settings-storage").assertIsDisplayed()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("storage-clear-all").fetchSemanticsNodes().any {
                    !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                }
            }
            fixture.capture("storage-overview")
            compose.onNode(hasText(context.getString(R.string.storage_lines)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").assertIsDisplayed()
            fixture.capture("storage-confirmation")
            pressBack()
            assertTrue(line.exists())
            compose.onNode(hasText(context.getString(R.string.storage_lines)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            compose.waitUntil(10_000) { !line.exists() }
            assertTrue(share.exists())
            assertTrue(export.exists())
            compose.onNodeWithTag("storage-clear-all").performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            compose.waitUntil(10_000) { !share.exists() && !export.exists() }
            assertEquals(8192, saved.length().toInt())
            assertEquals(8192, unrelated.length().toInt())
            assertEquals("\"retained\"", fixture.evaluate("localStorage.getItem('storage-device-test')"))
            compose.onNodeWithTag("storage-result").performScrollTo().assertIsDisplayed()
            fixture.capture("storage-after-cleanup")
            pressBack()
            compose.onNodeWithTag("settings-content").assertIsDisplayed()
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNodeWithTag("settings-storage").assertIsDisplayed()
        } finally {
            fixture.evaluate("localStorage.removeItem('storage-device-test')")
            fixture.close()
            listOf(line, share, export, saved, unrelated).forEach { it.delete() }
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, suppressed).commit()
            welcome.close()
        }
    }
}
