package cc.samlab.ticket

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingNotifications
import cc.samlab.ticket.tracking.TrainTrackingService
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainStationChipWidthProbeTest {
    @Test fun compareFourAndFiveCharactersInSystemChip() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = TrainTrackingDeviceFixture(instrumentation)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = TrainTrackingNotifications(context)
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        val output = File(context.getExternalFilesDir(null), "tracking-chip-width")
            .apply { mkdirs() }
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            fixture.open()
            TrainTrackingService.stop(context)
            val payload = JSONObject(fixture.countdownPayload())
            payload.getJSONArray("stops").getJSONObject(1).put("station", "乌鲁木齐南")
            val timeline = TrainTrackingStore.parse(payload.toString())
            val now = timeline.stops[1].arrival!!
            fixture.shell("cmd statusbar collapse")
            fixture.shell("input keyevent KEYCODE_HOME")
            for ((name, text) in listOf(
                "four" to "乌鲁木齐",
                "five" to "乌鲁木齐南",
                "five-shanghai" to "上海虹桥站",
            )) {
                val notice = Notification.Builder.recoverBuilder(context, notifications.build(timeline, now))
                    .setShortCriticalText(text)
                    .build()
                notifications.publish(notice)
                Thread.sleep(3_500)
                val published = manager.activeNotifications.first {
                    it.id == TrainTrackingNotifications.NOTIFICATION_ID
                }.notification
                assertEquals(text, published.shortCriticalText)
                println("Chip width probe: $name requested=$text flags=${published.flags}")
                val nodes = automation.windows.flatMap { window -> collectText(window.root) }
                    .filter { it.contains("systemui") }
                File(output, "$name.txt").writeText(nodes.joinToString("\n"))
                nodes.forEach(::println)
                automation.takeScreenshot()?.let { bitmap ->
                    val height = (context.resources.displayMetrics.density * 56).toInt()
                        .coerceAtMost(bitmap.height)
                    val statusBar = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
                    File(output, "$name.png").outputStream().use {
                        statusBar.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    statusBar.recycle()
                    bitmap.recycle()
                }
            }
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            fixture.restore()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val result = mutableListOf<String>()
        if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            result.add("${node.packageName} ${node.viewIdResourceName} " +
                "text=${node.text} description=${node.contentDescription} bounds=$bounds")
        }
        for (index in 0 until node.childCount) result.addAll(collectText(node.getChild(index)))
        return result
    }
}
