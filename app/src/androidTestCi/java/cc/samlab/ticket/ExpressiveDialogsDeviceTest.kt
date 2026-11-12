package cc.samlab.ticket

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressiveDialogsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun blockedTrackingAndMonitorNotificationsUseExpressivePrompts() {
        val fixture = TabletPageFixture(compose)
        val activity = fixture.activity
        val preferences = activity.getSharedPreferences("notifications", 0)
        val previous = if (preferences.contains("requested")) preferences.getBoolean("requested", false) else null
        try {
            assumeTrue(Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
            preferences.edit().putBoolean("requested", true).apply()
            dismissNotice()
            fixture.evaluate("""
                window.permissionResults = [];
                window.crticketNotificationResult = (id, result) => {
                    window.permissionResults.push([id, result.granted]);
                };
                CRTicketAndroid.requestTrainTrackingPermission('tracking');
                CRTicketAndroid.requestNotificationPermission('monitor');
            """.trimIndent())
            val tracking = activity.getString(R.string.notifications_enable_channel,
                activity.getString(R.string.tracking_channel_name))
            val monitor = activity.getString(R.string.notifications_enable_channel,
                activity.getString(R.string.notifications_ticket_channel))
            val decline = activity.getString(R.string.action_not_now)
            awaitPrompt(tracking)
            compose.onNodeWithText(activity.getString(R.string.action_open_settings)).assertIsDisplayed()
            fixture.capture("expressive-tracking-permission")
            compose.onNodeWithText(decline).performClick()
            awaitPrompt(monitor)
            pressBack()
            compose.waitUntil(10_000) { fixture.evaluate("window.permissionResults.length") == "2" }
            assertEquals(
                """[["tracking",false],["monitor",false]]""",
                fixture.evaluate("window.permissionResults"),
            )
            compose.onNodeWithTag("expressive-dialog").assertDoesNotExist()
        } finally {
            preferences.edit().apply {
                if (previous == null) remove("requested") else putBoolean("requested", previous)
            }.apply()
            fixture.close()
        }
    }

    @Test fun webViewAlertUsesExpressivePromptAndResumesScript() {
        val fixture = TabletPageFixture(compose)
        try {
            dismissNotice()
            fixture.navigate("/user")
            val message = "Expressive WebView alert"
            fixture.evaluate("setTimeout(() => { alert('$message'); window.alertCompleted = true }, 0); null")
            awaitPrompt(message)
            compose.onNodeWithText(fixture.activity.getString(R.string.action_confirm)).performClick()
            compose.waitUntil(10_000) { fixture.evaluate("window.alertCompleted") == "true" }
            compose.onNodeWithTag("expressive-dialog").assertDoesNotExist()
        } finally {
            fixture.close()
        }
    }

    private fun awaitPrompt(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("expressive-dialog").assertIsDisplayed()
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun dismissNotice() {
        if (compose.onAllNodesWithTag("important-notice-confirm").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("important-notice-confirm").performClick()
        }
    }
}
