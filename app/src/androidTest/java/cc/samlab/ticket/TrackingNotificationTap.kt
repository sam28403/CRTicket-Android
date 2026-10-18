package cc.samlab.ticket

import android.app.Instrumentation
import android.app.Notification
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Click the system notification itself, including Android's user-launch privileges. */
internal class TrackingNotificationTap(private val instrumentation: Instrumentation) : AutoCloseable {
    private val automation = instrumentation.uiAutomation
    private val originalFlags = automation.serviceInfo.flags

    init {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    fun click(notification: Notification) {
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        automation.executeShellCommand("cmd statusbar expand-notifications").close()
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val roots = listOfNotNull(automation.rootInActiveWindow) +
                automation.windows.mapNotNull { it.root }
            val node = roots.firstNotNullOfOrNull { findTitle(it, title) }
            if (node != null) {
                var target: AccessibilityNodeInfo = node
                while (!target.isClickable) target = target.parent ?: break
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            Thread.sleep(100)
        }
        throw AssertionError("Tracking notification was not clickable: $title")
    }

    private fun findTitle(node: AccessibilityNodeInfo, title: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == title) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findTitle(child, title)?.let { return it }
        }
        return null
    }

    override fun close() {
        automation.executeShellCommand("cmd statusbar collapse").close()
        automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
    }
}
