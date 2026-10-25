package cc.samlab.ticket

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.backup.BackupPermissionAccess
import cc.samlab.ticket.backup.BackupPermissionReview
import cc.samlab.ticket.sale.SaleReminderPreferences
import cc.samlab.ticket.sale.SaleReminderMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode get() = context.getSharedPreferences("sale-reminder-settings", Context.MODE_PRIVATE)
    private val query get() = context.getSharedPreferences("sale-time-query", Context.MODE_PRIVATE)

    @Test fun salePreferencesRoundTripAndInvalidInputDoesNotPartiallyRestore() {
        val saved = listOf(mode, query).associateWith { it.all.toMap() }
        try {
            mode.edit().putBoolean("alarm", true).commit()
            query.edit().putBoolean("sameCity", false).putString("history", "[\"北京南\"]").commit()
            val backup = BackupPreferences(context) { BackupPermissionAccess(true, true) }
            val snapshot = backup.snapshot()
            val value = JSONObject().put("saleAlarmEnabled", snapshot.getBoolean("saleAlarmEnabled"))
                .put("saleSameCity", snapshot.getBoolean("saleSameCity"))
                .put("saleSearchHistory", snapshot.getJSONArray("saleSearchHistory"))
            mode.edit().putBoolean("alarm", false).commit()
            query.edit().putBoolean("sameCity", true).remove("history").commit()
            backup.restore(value)
            assertEquals(SaleReminderMode.ALARM, SaleReminderPreferences.mode(context))
            assertFalse(query.getBoolean("sameCity", true))
            assertEquals("北京南", JSONArray(query.getString("history", "[]")).getString(0))
            try {
                backup.restore(JSONObject().put("saleAlarmEnabled", false)
                    .put("saleSearchHistory", JSONArray().put(12)))
                fail("Malformed backup accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(SaleReminderMode.ALARM, SaleReminderPreferences.mode(context))
            backup.restore(JSONObject())
            assertFalse(query.getBoolean("sameCity", true))
        } finally { saved.forEach { (preferences, values) -> restore(preferences, values) } }
    }

    @Test fun missingFullScreenPermissionIsReportedAfterImportAndPermissionPageShowsIt() {
        val saved = listOf(mode, query, BackupPermissionReview.preferences(context)).associateWith { it.all.toMap() }
        val fixture = TabletPageFixture(compose)
        val originalOp = Regex("USE_FULL_SCREEN_INTENT: (allow|ignore|deny|default)")
            .find(fixture.shell("cmd appops get ${context.packageName} USE_FULL_SCREEN_INTENT"))
            ?.groupValues?.get(1) ?: "default"
        try {
            fixture.shell("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT ignore")
            val backup = BackupPreferences(context)
            fixture.instrumentation.runOnMainSync {
                backup.restore(JSONObject().put("saleAlarmEnabled", true))
                backup.complete()
            }
            assertTrue(BackupPermissionReview.missing(context).contains(R.string.settings_sale_reminders))
            fixture.navigate("/settings")
            val message = context.getString(R.string.backup_permissions_review,
                context.getString(R.string.settings_sale_reminders))
            compose.onNodeWithText(message).assertIsDisplayed()
            fixture.capture("backup-missing-sale-permission")
            compose.onAllNodes(hasText(context.getString(R.string.settings_permissions)) and hasClickAction())
                .onLast().performClick()
            compose.onNodeWithText(context.getString(R.string.sale_mode_alarm)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.permissions_exact_alarms)).assertExists()
            compose.onNodeWithText(context.getString(R.string.permissions_dnd)).assertExists()
            fixture.capture("permissions-sale-alarm")
        } finally {
            fixture.shell("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT $originalOp")
            saved.forEach { (preferences, values) -> restore(preferences, values) }
            fixture.close()
        }
    }

    private fun restore(preferences: SharedPreferences, values: Map<String, *>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }
}
