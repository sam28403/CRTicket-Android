package cc.samlab.ticket

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.welcome.WelcomeActivity
import cc.samlab.ticket.update.UpdateInstallActivity
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Grant the special permission before starting instrumentation; changing it terminates the UID. */
@RunWith(AndroidJUnit4::class)
class UpdateInstallGrantedLiveTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun verifiedApkOpensSystemInstallerWithReadPermission() {
        assumeTrue(BuildConfig.UPDATE_TEST_TAG == "v0.9.4")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Grant installation source access before running this test",
            context.packageManager.canRequestPackageInstalls())
        val id = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
            .getLong("download:v0.9.4", -1)
        assertTrue(id > 0)
        assertNotNull(context.getSystemService(DownloadManager::class.java).getUriForDownloadedFile(id))
        val activity = instrumentation.startActivitySync(
            WelcomeActivity.releaseNotesIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as WelcomeActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.startActivity(UpdateInstallActivity.intent(activity, id))
            }
            var installerPackage = ""
            compose.waitUntil(30_000) {
                installerPackage = instrumentation.uiAutomation.rootInActiveWindow
                    ?.packageName?.toString().orEmpty()
                val installer = installerPackage.contains("packageinstaller") ||
                    installerPackage.contains("permissioncontroller")
                installer && instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("CRTicket")?.isNotEmpty() == true
            }
            File(context.getExternalFilesDir(null), "update-install-confirmation.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(context.getExternalFilesDir(null), "update-install-result.json").writeText(JSONObject()
                .put("installerPackage", installerPackage).put("downloadId", id)
                .put("installerOpened", true).toString(2))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK"),
            ).use { it.readBytes() }
            assertFalse(activity.isFinishing)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
