package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GeneratorLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = instrumentation.targetContext
        .getSharedPreferences("android", Context.MODE_PRIVATE)
    private var originalNotice: Boolean? = null
    private var wasInteractive = false

    @Before fun prepare() {
        wasInteractive = instrumentation.targetContext
            .getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        originalNotice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
    }

    @After fun restore() {
        val editor = preferences.edit()
        originalNotice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
            ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
        if (!wasInteractive) {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        }
    }

    @Test fun startupAndGeneratorColumnsFollowTheWindowSize() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            compose.waitUntil(30_000) {
                runCatching {
                    compose.onAllNodesWithTag("navigation-page:/home").fetchSemanticsNodes().isNotEmpty() ||
                        compose.onAllNodesWithTag("generator-editor").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            val wide = compose.onAllNodesWithTag("navigation-menu").fetchSemanticsNodes().isNotEmpty()
            if (wide) {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("generator-editor").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("navigation-page:/").assertExists()
            } else {
                compose.onNodeWithTag("navigation-page:/home").assertExists()
                scenario.onActivity { activity ->
                    activity.webView.evaluateJavascript("window.crticketNavigate('/')", null)
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("generator-editor").fetchSemanticsNodes().isNotEmpty()
                }
            }

            compose.onNodeWithTag("generator-other").performScrollTo()
            val journey = compose.onNodeWithTag("generator-journey").getUnclippedBoundsInRoot()
            val other = compose.onNodeWithTag("generator-other").getUnclippedBoundsInRoot()
            val preview = compose.onNodeWithTag("generator-preview-section").getUnclippedBoundsInRoot()
            val columnWidth = (other.right - other.left).value
            assertEquals((journey.right - journey.left).value, columnWidth, 1f)
            if (wide) {
                assertEquals(columnWidth, (preview.right - preview.left).value, 1f)
                assertEquals(other.top.value, preview.top.value, 1f)
                assertTrue(preview.left > other.right)
            } else {
                assertTrue(preview.top >= other.bottom)
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("generator-preview").fetchSemanticsNodes().isNotEmpty()
            }
            if (!wide) compose.onNodeWithTag("generator-preview").performScrollTo()
            val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "generator-layout")
            folder.mkdirs()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(folder, if (wide) "wide.png" else "phone.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
