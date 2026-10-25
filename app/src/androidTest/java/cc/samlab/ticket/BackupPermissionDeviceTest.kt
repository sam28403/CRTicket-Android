package cc.samlab.ticket

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPermissionAccess
import cc.samlab.ticket.backup.BackupPermissionReview
import cc.samlab.ticket.backup.BackupPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPermissionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun deniedPermissionsKeepSwitchesOffWhileRestoringOtherPreferences() {
        val arrival = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
        val sale = context.getSharedPreferences("sale-reminder-settings", Context.MODE_PRIVATE)
        val query = context.getSharedPreferences("sale-time-query", Context.MODE_PRIVATE)
        val preferences = listOf(arrival, sale, query, BackupPermissionReview.preferences(context))
        val original = preferences.associateWith { it.all.toMap() }
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_DENIED
        }
        try {
            val platformAccess = BackupPermissionAccess.read(denied)
            assertFalse(platformAccess.arrival)
            assertFalse(platformAccess.saleAlarm)
            arrival.edit().putBoolean("enabled", true).putBoolean("enableRequested", true).commit()
            sale.edit().putBoolean("alarm", true).commit()
            val requested = JSONObject().put("arrivalEnabled", true).put("saleAlarmEnabled", true)
                .put("saleSameCity", false).put("saleSearchHistory", JSONArray().put("北京南"))
            val backup = BackupPreferences(denied)
            backup.restore(requested)
            assertFalse(arrival.getBoolean("enabled", true))
            assertFalse(arrival.getBoolean("enableRequested", true))
            assertFalse(sale.getBoolean("alarm", true))
            assertFalse(query.getBoolean("sameCity", true))
            assertEquals("北京南", JSONArray(query.getString("history", "[]")).getString(0))
            assertTrue(requested.getBoolean("arrivalEnabled"))
            assertTrue(requested.getBoolean("saleAlarmEnabled"))
            instrumentation.runOnMainSync { backup.complete() }
            assertFalse(arrival.getBoolean("enabled", true))
            assertFalse(sale.getBoolean("alarm", true))
            BackupPermissionReview.dismiss(context) // Declining the follow-up never enables anything.
            assertFalse(arrival.getBoolean("enabled", true))
            assertFalse(sale.getBoolean("alarm", true))

            val granted = BackupPermissionAccess(true, true).filter(requested)
            assertTrue(granted.getBoolean("arrivalEnabled"))
            assertTrue(granted.getBoolean("saleAlarmEnabled"))
            val missingFields = BackupPermissionAccess(false, false).filter(JSONObject())
            assertFalse(missingFields.has("arrivalEnabled"))
            assertFalse(missingFields.has("saleAlarmEnabled"))
        } finally {
            original.forEach { (pref, values) ->
                pref.edit().clear().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Long -> putLong(key, value)
                            is Int -> putInt(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit()
            }
        }
    }
}
