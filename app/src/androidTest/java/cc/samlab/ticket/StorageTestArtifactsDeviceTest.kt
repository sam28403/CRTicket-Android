package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.StorageTestArtifacts
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageTestArtifactsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun testArtifactsCanBeCleanedAndTheRowDisappearsWhenNoneRemain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(StorageTestArtifacts.externalTargets(context, BuildConfig.CI_BUILD).isEmpty())
        assumeTrue(!BuildConfig.CI_BUILD || StorageTestArtifacts.privateTargets(context.filesDir).isEmpty())
        val welcome = WelcomeTestState(context)
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val suppressed = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val root = checkNotNull(context.getExternalFilesDir(null))
        val screenshot = File(root, "tablet-native-pages/storage-screenshot-test.png")
        val trace = File(root, "tablet-native-pages/storage-screenshot-test.json")
        val userImage = File(root, "storage-user-image-test.png")
        val archive = File(context.filesDir, "qa-storage-artifacts-test.tar")
        val artifacts = listOf(screenshot, trace) + listOf(archive).filter { BuildConfig.CI_BUILD }
        val fixtures = artifacts + userImage
        fixtures.forEach {
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
                    compose.onAllNodesWithTag("storage-category:TEST_ARTIFACTS")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
            fun artifactsRow() = compose.onNode(
                hasText(context.getString(R.string.storage_test_artifacts)) and hasClickAction(),
            )
            fun waitForHidden() {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("storage-category:TEST_ARTIFACTS")
                        .fetchSemanticsNodes().isEmpty()
                }
                artifacts.forEach { assertFalse(it.exists()) }
                assertTrue(userImage.exists())
            }
            openStorage()
            artifactsRow().performScrollTo().performClick()
            pressBack()
            artifacts.forEach { assertTrue(it.exists()) }
            artifactsRow().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            waitForHidden()
            // Even a zero-byte test artifact must remain manageable.
            val empty = if (BuildConfig.CI_BUILD) archive else trace
            empty.parentFile!!.mkdirs()
            empty.createNewFile()
            pressBack()
            openStorage()
            artifactsRow().assertIsEnabled()
            compose.onNodeWithTag("storage-clear-all").performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            waitForHidden()
            pressBack()
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("storage-total").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("storage-category:TEST_ARTIFACTS").assertDoesNotExist()
        } finally {
            fixture?.close()
            fixtures.forEach { it.delete() }
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, suppressed).commit()
            welcome.close()
        }
    }
}
