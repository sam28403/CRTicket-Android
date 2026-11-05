package cc.samlab.ticket

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PublicBenchmarkSmokeDeviceTest {
    @Test fun normalShrunkBenchmarkStartsWithoutDeveloperOptions() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("benchmarkSmoke") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.adoptShellPermissionIdentity("android.permission.QUERY_ALL_PACKAGES")
        try {
            val target = context
            val targetPackage = "cc.samlab.ticket"
            val resources = target.resources
            fun label(key: String): String {
                val id = resources.getIdentifier(key, "string", target.packageName)
                assertTrue("Missing public resource: $key", id != 0)
                return resources.getString(id)
            }
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            automation.executeShellCommand(
                "am start -W -n cc.samlab.ticket/cc.samlab.ticket.MainActivity",
            ).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    .bufferedReader().use { it.readText() }
            }
            await { automation.rootInActiveWindow?.packageName?.toString() == targetPackage }
            val welcomeButtons = listOfNotNull(
                runCatching { label("welcome_start") }.getOrNull(),
                runCatching { label("welcome_next") }.getOrNull(),
                "开始使用", "開始使用", "继续", "繼續", "Get started", "Continue",
            ).toSet()
            repeat(3) {
                val button = find(automation.rootInActiveWindow) {
                    it.text?.toString() in welcomeButtons
                } ?: return@repeat
                click(button)
                SystemClock.sleep(300)
            }
            val settings = label("home_settings")
            scrollFind(automation, settings).let(::click)
            await {
                val root = automation.rootInActiveWindow
                root?.packageName?.toString() == targetPackage &&
                    find(root) {
                        val text = it.text?.toString()
                        text == label("settings_display_name") ||
                            text == "显示名称" || text == "顯示名稱" || text == "Display name"
                    } != null
            }
            val folder = File(context.getExternalFilesDir(null), "logs/benchmark-normal-smoke")
                .apply { mkdirs() }
            val bitmap = requireNotNull(automation.takeScreenshot())
            File(folder, "settings.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            assertNull(find(automation.rootInActiveWindow) {
                it.text?.toString() in setOf("开发者选项", "開發者選項", "Developer options")
            })
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
            automation.dropShellPermissionIdentity()
        }
    }

    private fun scrollFind(automation: android.app.UiAutomation, text: String): AccessibilityNodeInfo {
        repeat(10) {
            val root = automation.rootInActiveWindow
            find(root) { it.text?.toString()?.startsWith(text) == true || it.contentDescription?.toString() == text }
                ?.let { return it }
            find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(300)
        }
        error("Public menu item not found: $text")
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("Menu item is not clickable", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun find(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Normal benchmark UI did not become ready")
    }
}
