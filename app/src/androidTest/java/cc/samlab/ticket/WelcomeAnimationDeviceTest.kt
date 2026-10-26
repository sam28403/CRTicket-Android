package cc.samlab.ticket

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.ui.welcome.WelcomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WelcomeAnimationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun welcomeSlidesForwardAndBackWhileTermsRemainRequired() {
        ActivityScenario.launch<WelcomeActivity>(WelcomeActivity.releaseNotesIntent(context)).use { scenario ->
            scenario.onActivity { activity ->
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent {
                    MaterialTheme(colorScheme = homeColorScheme(activity, false)) {
                        WelcomeScreen(true, "", onExit = {}, onComplete = {})
                    }
                }
            }
            compose.waitForIdle()
            val initial = compose.onNodeWithTag("welcome-page-0").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithTag("welcome-next").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(96)
                compose.waitForIdle()
                compose.onNodeWithTag("welcome-page-0").assertExists()
                val entering = compose.onNodeWithTag("welcome-page-1").fetchSemanticsNode().boundsInRoot
                assertTrue("Forward page did not enter from the right: $entering", entering.left > initial.left + 8)
                capture("welcome-forward-mid")
                compose.mainClock.advanceTimeBy(400)
                compose.waitForIdle()
                compose.onNodeWithTag("welcome-page-0").assertDoesNotExist()
                compose.onNodeWithText(context.getString(R.string.welcome_read_all)).assertIsNotEnabled()

                pressBack()
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(96)
                compose.waitForIdle()
                val leaving = compose.onNodeWithTag("welcome-page-1").fetchSemanticsNode().boundsInRoot
                assertTrue("Back page did not move right: $leaving", leaving.left > initial.left + 8)
                compose.onNodeWithTag("welcome-page-0").assertExists()
                capture("welcome-back-mid")
                compose.mainClock.advanceTimeBy(400)
                compose.waitForIdle()
                compose.onNodeWithTag("welcome-page-1").assertDoesNotExist()
                compose.onNodeWithTag("welcome-next").assertIsDisplayed()
            } finally { compose.mainClock.autoAdvance = true }
        }
    }

    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "welcome-animation").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
