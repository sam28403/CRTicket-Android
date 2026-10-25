package cc.samlab.ticket

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.update.UpdateHost
import cc.samlab.ticket.update.updates
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real GitHub download. Run against the isolated updateTest variant on one device. */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadLiveTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun downloadsPublished094WithoutNotificationPermissionAndHandlesDeniedInstallPermission() {
        assumeTrue(BuildConfig.UPDATE_TEST_TAG == "v0.9.4")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("pm revoke ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val manager = context.getSystemService(DownloadManager::class.java)
        val previous = preferences.getLong("download:v0.9.4", -1)
        val alreadyDownloaded = previous > 0 && manager.query(
            DownloadManager.Query().setFilterById(previous),
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(
                DownloadManager.COLUMN_STATUS,
            )) == DownloadManager.STATUS_SUCCESSFUL
        }
        val activity = instrumentation.startActivitySync(
            WelcomeActivity.releaseNotesIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as WelcomeActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.updates().changeAutomatic(false)
                activity.setContent { UpdateHost(activity, dark = false, pureBlack = false) }
                activity.updates().check()
            }
            compose.waitUntil(60_000) { activity.updates().release != null }
            assertEquals("v0.9.4", activity.updates().release?.tag)
            if (!alreadyDownloaded) {
                compose.onNodeWithText(context.getString(R.string.update_download)).performClick()
            }
            val id = preferences.getLong("download:v0.9.4", -1)
            assertTrue("DownloadManager did not enqueue the update", id > 0)
            var bytes = 0L
            var total = 0L
            var progressed = false
            var complete = false
            val deadline = SystemClock.elapsedRealtime() + 240_000
            while (SystemClock.elapsedRealtime() < deadline) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    assertNotEquals("Download failed, reason=$reason", DownloadManager.STATUS_FAILED, status)
                    progressed = progressed || (bytes > 0 && status == DownloadManager.STATUS_RUNNING)
                    complete = status == DownloadManager.STATUS_SUCCESSFUL
                }
                if (complete) break
                SystemClock.sleep(500)
            }
            assertTrue("Download did not complete, bytes=$bytes total=$total", complete)
            assertEquals(50_691_142L, bytes)
            assertEquals(total, bytes)
            val uri = requireNotNull(manager.getUriForDownloadedFile(id))
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals("86caba4fa312e94330f370e6e948d5395ebf63dfa1cfe0d6a128fcd06843b1d6", sha256)
            assertFalse(context.packageManager.canRequestPackageInstalls())
            instrumentation.runOnMainSync {
                activity.updates().dismiss()
                activity.updates().check()
            }
            compose.waitUntil(60_000) { activity.updates().release != null }
            compose.onNodeWithText(context.getString(R.string.update_download)).performClick()
            compose.waitUntil(10_000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings"
            }
            screenshot("update-install-permission.png")
            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName
            }
            assertFalse(context.packageManager.canRequestPackageInstalls())
            assertFalse(activity.isFinishing)
            File(context.getExternalFilesDir(null), "update-download-result.json").writeText(JSONObject()
                .put("downloadId", id).put("bytes", bytes).put("sha256", sha256)
                .put("progressObserved", progressed).put("status", "successful")
                .put("reusedCompletedDownload", alreadyDownloaded)
                .put("appNotificationPermission", "denied")
                .put("unknownSourcesPermission", "denied safely after settings return")
                .toString(2))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun screenshot(name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).use { it.readBytes() }
    }
}
