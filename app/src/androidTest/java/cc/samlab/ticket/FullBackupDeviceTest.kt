package cc.samlab.ticket

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorStore
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.i18n.AppLanguages
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var wasInteractive = false

    @Before fun wakeDevice() {
        wasInteractive = context.getSystemService(PowerManager::class.java).isInteractive
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP"),
        ).use { it.readBytes() }
    }

    @After fun restoreScreen() {
        if (!wasInteractive) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        }
    }

    @Test fun languageRestorationWaitsForSuccessfulImportCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = android.all[IMPORTANT_NOTICE_SUPPRESSED]
        var original = AppLanguages.selectedTag()
        android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    scenario.onActivity { original = AppLanguages.selectedTag() }
                    val language = if (original == "en") "zh-CN" else "en"
                    scenario.onActivity { activity ->
                        activity.setShowWhenLocked(true)
                        activity.setTurnScreenOn(true)
                        val preferences = BackupPreferences(activity)
                        preferences.restore(JSONObject().put("language", language))
                        assertEquals(original, AppLanguages.selectedTag())
                        preferences.complete()
                        assertEquals(language, AppLanguages.selectedTag())
                    }
                    var actual = ""
                    try {
                        compose.waitUntil(20_000) {
                            var applied = false
                            scenario.onActivity { activity ->
                                activity.setShowWhenLocked(true)
                                activity.setTurnScreenOn(true)
                                applied = activity.getString(R.string.settings_language) ==
                                    if (language == "en") "Language" else "语言"
                                actual = "${AppLanguages.selectedTag()}: " +
                                    "${activity.resources.configuration.locales}: " +
                                    activity.getString(R.string.settings_language)
                            }
                            applied
                        }
                    } catch (error: Exception) {
                        throw AssertionError("Language restore did not update resources: $actual", error)
                    }
                    assertEquals(language, AppLanguages.selectedTag())
                } finally {
                    instrumentation.runOnMainSync { AppLanguages.select(original) }
                    compose.waitUntil(15_000) { AppLanguages.selectedTag() == original }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            restorePreferences(android, mapOf(IMPORTANT_NOTICE_SUPPRESSED to notice))
        }
    }

    @Test fun fullBackupRoundTripAndLegacyImportPreserveExistingData() {
        check(!TicketMonitorStore.read(context).optBoolean("active")) {
            "Stop the device's ticket monitor before running this fixture"
        }
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val arrival = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
        val monitor = context.getSharedPreferences("ticket-monitor", Context.MODE_PRIVATE)
        val original = mapOf(
            android to listOf(IMPORTANT_NOTICE_SUPPRESSED, "themePreference", "appearanceDark", "appearancePureBlack"),
            arrival to listOf("enabled", "message", "registered"),
            monitor to listOf("state"),
        ).mapValues { (preferences, keys) -> keys.associateWith { preferences.all[it] } }
        android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity {
                    activity = it
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                }
                val fixture = BackupDeviceFixture(compose, activity)
                fixture.ready()
                val storage = fixture.storage()
                try {
                    val backup = fixture.export()
                    assertEquals(2, backup.getInt("schemaVersion"))
                    val settings = backup.getJSONObject("settings")
                    val native = settings.getJSONObject("native")
                    assertTrue(native.has("language"))
                    assertTrue(native.has("arrivalEnabled"))
                    assertTrue(native.has("monitorConfig"))
                    assertFalse(native.has("active"))
                    settings.getJSONObject("profile").put("username", "完整备份验证")
                    settings.put("theme", "contrast-dark")
                    val ticket = JSONObject()
                        .put("ticket_number", "EBACKUP-" + System.currentTimeMillis())
                        .put("train_no", "G1").put("departure_station", "北京南")
                        .put("arrival_station", "上海虹桥").put("travel_date", "2026年10月10日")
                        .put("departure_time", "09:00").put("price", 0).put("distance", 1318)
                        .put("seat_type", "二等座").put("seat_no", "01车01A号")
                        .put("created_at", "2026-01-02T03:04:05.000Z")
                    backup.getJSONArray("tickets").put(ticket).put(ticket)
                    val restored = fixture.import(backup.toString(), settings = true)
                    assertTrue(restored.toString(), restored.getBoolean("success"))
                    assertEquals("完整备份验证", restored.getJSONObject("state").getString("username"))
                    assertTrue(restored.getJSONObject("state").getBoolean("pureBlack"))
                    val after = fixture.storage()
                    val rows = JSONArray(after.getString("crticket-device-tickets-v1"))
                    assertEquals(backup.getJSONArray("tickets").length() - 1, rows.length())
                    val imported = (0 until rows.length()).map { rows.getJSONObject(it) }
                        .single { it.optString("ticket_number") == ticket.getString("ticket_number") }
                    assertEquals(ticket.getString("created_at"), imported.getString("created_at"))
                    assertEquals(0, imported.getInt("price"))

                    // Invalid native settings must fail before even the new ticket is written.
                    val invalid = JSONObject(backup.toString())
                    invalid.getJSONObject("settings").getJSONObject("native").put("language", "invalid")
                    invalid.getJSONArray("tickets").put(JSONObject(ticket.toString()).put("ticket_number", "INVALID"))
                    assertFalse(fixture.import(invalid.toString(), settings = true).getBoolean("success"))
                    assertEquals(after.toString(), fixture.storage().toString())

                    assertTrue(fixture.import("[null]", settings = false).getBoolean("success"))
                    assertEquals(after.toString(), fixture.storage().toString())
                    val reExported = fixture.export()
                    assertEquals("完整备份验证", reExported.getJSONObject("settings")
                        .getJSONObject("profile").getString("username"))
                } finally {
                    fixture.restoreStorage(storage)
                }
            }
        } finally {
            original.forEach { (preferences, values) -> restorePreferences(preferences, values) }
        }
    }

    private fun restorePreferences(preferences: SharedPreferences, values: Map<String, Any?>) {
        preferences.edit().apply {
            values.forEach { (key, value) ->
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                }
            }
        }.commit()
    }
}
