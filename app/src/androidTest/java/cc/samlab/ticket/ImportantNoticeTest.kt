package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportantNoticeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("android", Context.MODE_PRIVATE)
    private var original: Boolean? = null

    @Before fun prepare() {
        original = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        preferences.edit().remove(IMPORTANT_NOTICE_SUPPRESSED).commit()
    }

    @After fun restore() {
        val editor = preferences.edit()
        original?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
            ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
        editor.commit()
    }

    @Test fun confirmationWithoutOptOutLastsOnlyForThisLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("important-notice").assertIsDisplayed()
            capture()
            compose.onNodeWithTag("important-notice-confirm").performClick()
            compose.onNodeWithTag("important-notice").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithTag("important-notice").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("important-notice").assertIsDisplayed()
        }
        assertFalse(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
    }

    @Test fun optOutIsSavedOnlyWhenConfirmedAndSurvivesANewLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("important-notice-opt-out").performClick()
            compose.onNodeWithTag("important-notice-opt-out").assertIsOn()
            assertFalse(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
            scenario.recreate()
            compose.onNodeWithTag("important-notice-opt-out").assertIsOn()
            compose.onNodeWithTag("important-notice-confirm").performClick()
            compose.onNodeWithTag("important-notice").assertDoesNotExist()
        }
        assertTrue(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("important-notice").assertDoesNotExist()
        }
    }

    private fun capture() {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "important-notice")
        folder.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "startup.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
