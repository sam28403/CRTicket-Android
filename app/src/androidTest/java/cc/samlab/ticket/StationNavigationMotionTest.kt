package cc.samlab.ticket

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StationNavigationMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Test fun shanghaiToBeijingAnimatesAndEveryBackFrameKeepsItsOwnStation() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
            evaluate("window.crticketNavigate('/station?station=' + encodeURIComponent('上海虹桥'))")
            awaitBoard("上海虹桥")
            val list = compose.onNodeWithTag("station-board")
            list.performTouchInput { swipeUp() }
            list.performScrollToNode(
                hasAnyDescendant(hasTestTag("station-name") and hasText("北京南")),
            )
            compose.waitForIdle()
            val original = position("上海虹桥")!!
            compose.mainClock.autoAdvance = false
            compose.onAllNodes(
                hasTestTag("station-name") and hasText("北京南"), useUnmergedTree = true,
            ).onFirst().performClick()

            val positions = mutableListOf<Float>()
            var wrongFrames = 0
            repeat(100) {
                compose.mainClock.advanceTimeBy(16)
                Thread.sleep(16)
                position("北京南")?.let(positions::add)
                wrongFrames += wrongStationFrames()
            }
            compose.mainClock.autoAdvance = true
            awaitBoard("北京南")
            val target = position("北京南")!!
            val intermediate = positions.distinct().count { it > 0f && it < target - 0.01f }
            capture("beijing")

            compose.mainClock.autoAdvance = false
            pressBack()
            repeat(60) {
                compose.mainClock.advanceTimeBy(16)
                Thread.sleep(16)
                wrongFrames += wrongStationFrames()
            }
            compose.mainClock.autoAdvance = true
            awaitBoard("上海虹桥")
            capture("shanghai-restored")
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("stream", "Station motion: target=$target, intermediate=$intermediate, wrongStationFrames=$wrongFrames\n")
            })
            assertEquals(original, position("上海虹桥")!!, 0.001f)
            assertEquals("A navigation page displayed another station's content", 0, wrongFrames)
            assertTrue("No visible scroll animation to the current train: $positions", intermediate >= 3)

            // Visit the cached destination again and exercise Android's actual edge gesture.
            compose.onAllNodes(
                hasTestTag("station-name") and hasText("北京南"), useUnmergedTree = true,
            ).onFirst().performClick()
            awaitBoard("北京南")
            compose.mainClock.autoAdvance = false
            val metrics = activity.resources.displayMetrics
            val gesture = instrumentation.uiAutomation.executeShellCommand(
                "input swipe 1 ${metrics.heightPixels / 2} " +
                    "${metrics.widthPixels * 3 / 4} ${metrics.heightPixels / 2} 500",
            )
            try {
                repeat(80) {
                    compose.mainClock.advanceTimeBy(16)
                    Thread.sleep(16)
                    wrongFrames += wrongStationFrames()
                }
            } finally { gesture.close() }
            compose.mainClock.autoAdvance = true
            awaitBoard("上海虹桥")
            assertEquals("Predictive back displayed another station's content", 0, wrongFrames)
            assertEquals(original, position("上海虹桥")!!, 0.001f)
        } finally {
            compose.mainClock.autoAdvance = true
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun page(name: String) = hasTestTag(
        "navigation-page:/station?station=${android.net.Uri.encode(name)}",
    )

    private fun position(name: String): Float? = compose.onAllNodes(
        hasTestTag("station-board") and hasAnyAncestor(page(name)),
    ).fetchSemanticsNodes().firstOrNull()?.config
        ?.get(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()

    private fun wrongStationFrames(): Int = listOf("上海虹桥", "北京南").sumOf { name ->
        compose.onAllNodes(
            hasTestTag("station-query") and hasAnyAncestor(page(name)),
        ).fetchSemanticsNodes().count {
            it.config[SemanticsProperties.EditableText].text != name
        }
    }

    private fun awaitBoard(name: String) {
        compose.waitUntil(120_000) {
            evaluate("new URLSearchParams(location.hash.split('?')[1]).get('station')") == JSONObject.quote(name) &&
                runCatching { position(name) != null }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "station-motion")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            activity.webView.evaluateJavascript(script) { result = it; latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return result
    }
}
