package cc.samlab.ticket

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.PowerManager
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.trainquery.NativeTrainQueryController
import cc.samlab.ticket.ui.trainquery.TrainQueryControls
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainQueryLabelFitTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    private data class Case(
        val width: Int,
        val fontScale: Float,
        val date: String,
        val tag: String = "en",
    )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun dateLabelFitsExistingControls() {
        val preferences = target.getSharedPreferences("android", Context.MODE_PRIVATE)
        val originalNotice = if (preferences.contains(IMPORTANT_NOTICE_SUPPRESSED)) {
            preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        } else null
        val interactive = target.getSystemService(PowerManager::class.java).isInteractive
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        val currentCase = mutableStateOf(Case(320, 1f, "2026-10-19"))
        val measurements = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var controller: NativeTrainQueryController? = null
                var webView: WebView? = null
                scenario.onActivity { activity ->
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                    val web = WebView(activity).also { webView = it }
                    val query = NativeTrainQueryController(activity, web).also { controller = it }
                    activity.findViewById<ComposeView>(R.id.expressive_navigation_view).setContent {
                        val sample by currentCase
                        val configuration = remember(sample.fontScale, sample.tag) {
                            Configuration(activity.resources.configuration).apply {
                                setLocales(LocaleList.forLanguageTags(sample.tag))
                                fontScale = sample.fontScale
                            }
                        }
                        val context = remember(configuration) {
                            activity.createConfigurationContext(configuration)
                        }
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(
                            LocalContext provides context,
                            LocalConfiguration provides configuration,
                            LocalDensity provides Density(density, sample.fontScale),
                            LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                                LayoutDirection.Rtl
                            } else {
                                LayoutDirection.Ltr
                            },
                        ) {
                            MaterialExpressiveTheme {
                                Box(Modifier.widthIn(max = sample.width.dp).fillMaxWidth()) {
                                    Surface {
                                        TrainQueryControls(
                                            query,
                                            JSONObject().put("train", "G1").put("date", sample.date),
                                            current = false,
                                            onDate = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                try {
                    val samples = mutableListOf<Case>()
                    for (width in listOf(320, 360)) {
                        for (scale in listOf(1f, 1.3f)) {
                            for (date in listOf("2026-10-19", "")) {
                                samples += Case(width, scale, date)
                            }
                        }
                    }
                    val additional = supportedTestLocales(target)
                        .filterNot { it == "en" || it.startsWith("zh-") }
                    for (tag in additional) {
                        for (date in listOf("2026-10-19", "")) {
                            samples += Case(320, 1.3f, date, tag)
                        }
                    }
                    for (sample in samples) {
                        val locale = target.createConfigurationContext(
                            Configuration(target.resources.configuration).apply {
                                setLocales(LocaleList.forLanguageTags(sample.tag))
                            },
                        )
                        val label = locale.getString(R.string.train_query_start_date)
                        compose.runOnIdle { currentCase.value = sample }
                        compose.waitForIdle()
                        val layouts = mutableListOf<TextLayoutResult>()
                        compose.onAllNodesWithText(label, useUnmergedTree = true).onFirst()
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                                it(layouts)
                            }
                        assertTrue("Missing label layout", layouts.isNotEmpty())
                        val layout = layouts.single()
                        val message = "$sample label=$label lines=${layout.lineCount} " +
                            "overflowWidth=${layout.didOverflowWidth} " +
                            "overflowHeight=${layout.didOverflowHeight} " +
                            "widthPx=${layout.size.width} heightPx=${layout.size.height}"
                        println(message)
                        measurements.add(message)
                        capture("${sample.tag}-${sample.width}-${sample.fontScale}-" +
                            if (sample.date.isEmpty()) "empty" else "filled")
                        assertEquals(message, 1, layout.lineCount)
                        // TextLayoutResult rounds its size to whole pixels;
                        // compare glyph bounds against the real constraint.
                        assertTrue(
                            message,
                            layout.getLineRight(0) <= layout.layoutInput.constraints.maxWidth + 1f,
                        )
                        assertTrue(message, !layout.isLineEllipsized(0))
                    }
                    File(output(), "measurements.txt").writeText(measurements.joinToString("\n"))
                } finally {
                    scenario.onActivity {
                        controller?.close()
                        webView?.destroy()
                    }
                }
            }
        } finally {
            val editor = preferences.edit()
            originalNotice?.let { editor.putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                ?: editor.remove(IMPORTANT_NOTICE_SUPPRESSED)
            editor.commit()
            if (!interactive) {
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            }
        }
    }

    private fun output() = File(target.getExternalFilesDir(null), "train-query-label-fit")
        .apply { mkdirs() }

    private fun capture(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output(), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
