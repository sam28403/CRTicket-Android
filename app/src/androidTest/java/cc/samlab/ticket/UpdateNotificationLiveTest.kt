package cc.samlab.ticket

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.update.AppRelease
import cc.samlab.ticket.update.UpdateDownloadResult
import cc.samlab.ticket.update.UpdateDownloads
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reads only this test's download notification fields; never records other notifications. */
@RunWith(AndroidJUnit4::class)
class UpdateNotificationLiveTest {
    @Test fun systemDisplaysProgressEvenWhenAppNotificationsAreDenied() {
        assumeTrue(BuildConfig.UPDATE_TEST_TAG == "v0.9.4")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val tag = "v0.9.4-notification-test-${SystemClock.elapsedRealtime()}"
        val release = AppRelease(
            tag, "",
            "https://github.com/sam28403/CRTicket-Android/releases/download/v0.9.4/CRTicket-v0.9.4.apk",
        )
        val task = UpdateDownloads(context).enqueue(release) as UpdateDownloadResult.Started
        val manager = context.getSystemService(DownloadManager::class.java)
        var notificationObserved = false
        var progress = -1
        var maximum = -1
        try {
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys notification --noredact"),
                ).bufferedReader().use { it.readText() }
                val record = dump.split("NotificationRecord(").firstOrNull {
                    it.contains("CRTicket $tag") && it.contains("pkg=com.android.providers.downloads")
                }
                if (record != null) {
                    progress = Regex("android.progress=(?:Int|Integer) \\((\\d+)\\)")
                        .find(record)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    maximum = Regex("android.progressMax=(?:Int|Integer) \\((\\d+)\\)")
                        .find(record)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    notificationObserved = maximum > 0 && progress in 0 until maximum
                    if (notificationObserved) break
                }
                manager.query(DownloadManager.Query().setFilterById(task.id)).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertNotEquals(DownloadManager.STATUS_FAILED,
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
                }
                SystemClock.sleep(100)
            }
            File(context.getExternalFilesDir(null), "update-notification-result.json").writeText(JSONObject()
                .put("systemProgressNotificationObserved", notificationObserved)
                .put("progress", progress).put("maximum", maximum).toString(2))
            assertTrue("No system download progress notification observed", notificationObserved)
        } finally {
            manager.remove(task.id)
            context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit()
                .remove("download:$tag").apply()
        }
    }
}
