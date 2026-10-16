package cc.samlab.ticket

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cc.samlab.ticket.arrival.ArrivalNotifications
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ArrivalNotificationNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: MainActivity

    @Test fun notificationShadeClickOpensCorrectStationWithRunningAndClosedActivity() {
        org.junit.Assume.assumeFalse(
            "Unlock the device before testing notification-shade clicks",
            context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked,
        )
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val notifications = ArrivalNotifications(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val previous = manager.activeNotifications.firstOrNull { it.tag == "station-arrival" && it.id == 401 }
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as MainActivity
            keepAwake()
            compose.waitUntil(20_000) { evaluate("Boolean(window.crticketNavigate)") == "true" }
            evaluate("window.crticketNavigate('/train')")
            compose.waitUntil(20_000) { evaluate("location.hash") == "\"#/train\"" }

            assertTrue("Arrival notification permission is required", notifications.publish("上海虹桥"))
            tapNotification("欢迎到访上海虹桥站")
            awaitStation("上海虹桥")
            report("Notification shade tap with running activity: 上海虹桥 native board loaded")

            val oldActivity = activity
            instrumentation.runOnMainSync { oldActivity.finish() }
            instrumentation.waitForIdleSync()
            assertTrue(notifications.publish("北京南"))
            tapNotification("欢迎到访北京南站")
            compose.waitUntil(20_000) {
                var resumed: MainActivity? = null
                instrumentation.runOnMainSync {
                    resumed = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                        .firstOrNull { it !== oldActivity }
                }
                resumed?.also { activity = it } != null
            }
            keepAwake()
            assertNotSame(oldActivity, activity)
            awaitStation("北京南")
            report("Notification shade tap with no existing activity: 北京南 native board loaded")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
            manager.cancel("station-arrival", 401)
            if (previous != null) manager.notify(previous.tag, previous.id, previous.notification)
            if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun keepAwake() = instrumentation.runOnMainSync {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun tapNotification(title: String) {
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val roots = listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow) +
                instrumentation.uiAutomation.windows.mapNotNull { it.root }
            val node = roots.firstNotNullOfOrNull { findTitle(it, title) }
            if (node != null) {
                var target: AccessibilityNodeInfo = node
                while (!target.isClickable) target = target.parent ?: break
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            Thread.sleep(100)
        }
        fail("Arrival notification was not clickable in the notification shade: $title")
    }

    private fun findTitle(node: AccessibilityNodeInfo?, title: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == title) return node
        for (index in 0 until node.childCount) findTitle(node.getChild(index), title)?.let { return it }
        return null
    }

    private fun awaitStation(name: String) {
        compose.waitUntil(120_000) {
            runCatching {
                compose.onNodeWithTag("station-query").fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText].text == name &&
                    compose.onAllNodesWithTag("station-board").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(org.json.JSONObject.quote(name), evaluate(
            "new URLSearchParams(location.hash.split('?')[1]).get('station')",
        ))
        compose.onNodeWithTag("station-screen").assertIsDisplayed()
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

    private fun report(text: String) = instrumentation.sendStatus(2, android.os.Bundle().apply {
        putString("stream", "$text\n")
    })
}
