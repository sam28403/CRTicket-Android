package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.StorageTestScreenshots
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageScreenshotDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun screenshotsCanBeCleanedAndTheRowDisappearsWhenNoneRemain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val suppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val root = checkNotNull(context.getExternalFilesDir(null))
        val screenshot = File(root, "tablet-native-pages/storage-screenshot-test.png")
        val trace = File(root, "tablet-native-pages/storage-screenshot-test.json")
        val userImage = File(root, "storage-user-image-test.png")
        listOf(screenshot, trace, userImage).forEach {
            it.parentFile!!.mkdirs()
            it.writeBytes(ByteArray(4096) { 42 })
        }
        var fixture: TabletPageFixture? = null
        try {
            val page = TabletPageFixture(compose).also { fixture = it }
            page.navigate("/settings")
            fun openStorage() {
                compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                    .performScrollTo().performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("storage-category:TEST_SCREENSHOTS")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
            fun screenshotRow() = compose.onNode(
                hasText(context.getString(R.string.storage_test_screenshots)) and hasClickAction(),
            )
            fun waitForHidden() {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("storage-category:TEST_SCREENSHOTS")
                        .fetchSemanticsNodes().isEmpty()
                }
                assertTrue(StorageTestScreenshots.files(context).isEmpty())
                assertTrue(trace.exists())
                assertTrue(userImage.exists())
            }
            openStorage()
            screenshotRow().performScrollTo().performClick()
            pressBack()
            assertTrue(screenshot.exists())
            screenshotRow().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            waitForHidden()
            // Even a zero-byte test image must be manageable, then disappear after cleanup.
            screenshot.createNewFile()
            pressBack()
            openStorage()
            screenshotRow().assertIsEnabled()
            compose.onNodeWithTag("storage-clear-all").performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            waitForHidden()
            pressBack()
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("storage-total").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("storage-category:TEST_SCREENSHOTS").assertDoesNotExist()
        } finally {
            fixture?.close()
            listOf(screenshot, trace, userImage).forEach { it.delete() }
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, suppressed).commit()
            welcome.close()
        }
    }
}
