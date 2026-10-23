package cc.samlab.ticket

import android.content.Context
import android.graphics.Bitmap
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.report.reportDeviceInfo
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.ui.welcome.WelcomeState
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotesDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun updatedWelcomeUsesLineBreakAndRendersMarkdown() {
        val preferences = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
        val terms = preferences.getInt("terms", 0)
        val version = preferences.getString("version", null)
        try {
            WelcomeState.complete(context)
            ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                }
                val title = context.getString(R.string.welcome_updated_title)
                assertTrue(title.contains('\n'))
                compose.onNodeWithText(title).assertExists()
                capture("updated-welcome")
                compose.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val text = textViews(activity.window.decorView)
                        .map { it.text }.filterIsInstance<Spanned>()
                        .first { it.toString().contains("Sam-Lab CRTicket") }
                    assertFalse(text.toString().contains("# ${WelcomeState.version}"))
                    assertFalse(text.toString().contains("**Sam-Lab CRTicket**"))
                    val spans = text.getSpans(0, text.length, Any::class.java)
                    assertTrue(spans.any { it.javaClass.simpleName == "HeadingSpan" })
                    assertTrue(spans.any { it.javaClass.simpleName == "StrongEmphasisSpan" })
                }
                capture("release-notes")
            }
        } finally {
            preferences.edit().putInt("terms", terms).putString("version", version).commit()
        }
    }

    @Test fun optionalDeviceInfoIncludesGmsAndCurrentWebView() {
        val info = reportDeviceInfo(context)
        assertTrue(info.contains("GMS: "))
        assertTrue(info.contains("WebView: "))
        assertFalse(info.contains("null"))
        android.webkit.WebView.getCurrentWebViewPackage()?.let {
            assertTrue(info.contains(it.packageName))
            assertTrue(info.contains(it.versionName.orEmpty()))
        }
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "welcome-scenes").apply { mkdirs() }
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
