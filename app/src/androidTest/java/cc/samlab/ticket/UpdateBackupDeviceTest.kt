package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.BackupContent
import cc.samlab.ticket.ui.settings.availableBackupContents
import cc.samlab.ticket.update.updates
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    @Test fun updatePreferencesValidateBeforeWritingAndOlderBackupsPreserveChoices() = preservingChoices {
        val backup = BackupPreferences(context)
        preferences.edit().remove("previews").remove("automatic").commit()
        assertFalse(backup.snapshot().getBoolean("updatePreviews"))
        assertTrue(backup.snapshot().getBoolean("updateAutomatic"))
        preferences.edit().putBoolean("previews", true).putBoolean("automatic", false).commit()
        val exported = backup.snapshot()
        assertTrue(exported.getBoolean("updatePreviews"))
        assertFalse(exported.getBoolean("updateAutomatic"))
        assertFalse(exported.has("download:v0.9.4"))
        val downloads = preferences.all.filterKeys { it.startsWith("download:") }
        backup.restore(JSONObject().put("updatePreviews", false).put("updateAutomatic", true))
        assertFalse(preferences.getBoolean("previews", true))
        assertTrue(preferences.getBoolean("automatic", false))
        for (key in listOf("updatePreviews", "updateAutomatic")) {
            for (invalid in listOf(JSONObject.NULL, "false", 0, 1, JSONArray(), JSONObject())) {
                assertTrue(runCatching {
                    backup.restore(JSONObject().put("updatePreviews", true)
                        .put("updateAutomatic", false).put(key, invalid))
                }.isFailure)
                assertFalse(preferences.getBoolean("previews", true))
                assertTrue(preferences.getBoolean("automatic", false))
            }
            assertEquals(setOf(BackupContent.SETTINGS), availableBackupContents(envelope(
                JSONObject().put(key, false),
            )))
        }
        backup.restore(JSONObject())
        assertFalse(preferences.getBoolean("previews", true))
        assertTrue(preferences.getBoolean("automatic", false))
        backup.restore(JSONObject().put("updatePreviews", true))
        assertTrue(preferences.getBoolean("previews", false))
        assertTrue(preferences.getBoolean("automatic", false))
        assertEquals(downloads, preferences.all.filterKeys { it.startsWith("download:") })
    }

    @Test fun selectedSettingsRoundTripThroughExportImportAndRefreshTheLiveScreen() = preservingChoices {
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = android.all[IMPORTANT_NOTICE_SUPPRESSED]
        preferences.edit().putBoolean("previews", true).putBoolean("automatic", false).commit()
        WelcomeTestState(context).use {
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
                        val excluded = BackupContent.entries.filter { it != BackupContent.SETTINGS }
                            .map { context.getString(it.label) }.toSet()
                        val exported = fixture.export(excluded)
                        val native = exported.getJSONObject("settings").getJSONObject("native")
                        assertTrue(native.getBoolean("updatePreviews"))
                        assertFalse(native.getBoolean("updateAutomatic"))
                        scenario.onActivity {
                            it.updates().changePreviews(false)
                            it.updates().changeAutomatic(true)
                        }
                        assertTrue(fixture.import(exported.toString(), settings = true).getBoolean("success"))
                        compose.waitUntil(5_000) {
                            activity.updates().previews && !activity.updates().automatic
                        }
                        assertTrue(preferences.getBoolean("previews", false))
                        assertFalse(preferences.getBoolean("automatic", true))

                        val withoutSettings = fixture.export(setOf(context.getString(R.string.backup_content_settings)))
                        val excludedNative = withoutSettings.getJSONObject("settings").optJSONObject("native")
                        assertFalse(excludedNative?.has("updatePreviews") == true)
                        assertFalse(excludedNative?.has("updateAutomatic") == true)

                        scenario.onActivity {
                            it.updates().changePreviews(false)
                            it.updates().changeAutomatic(true)
                        }
                        val selectable = JSONObject(exported.toString())
                            .put("contents", JSONArray(listOf("tickets", "settings")))
                        assertTrue(fixture.import(selectable.toString(), settings = true,
                            excluded = setOf(context.getString(R.string.backup_content_settings)))
                            .getBoolean("success"))
                        assertFalse(preferences.getBoolean("previews", true))
                        assertTrue(preferences.getBoolean("automatic", false))

                        val older = envelope(JSONObject().put("importantNoticeSuppressed", true))
                        assertTrue(fixture.import(older.toString(), settings = true).getBoolean("success"))
                        assertFalse(preferences.getBoolean("previews", true))
                        assertTrue(preferences.getBoolean("automatic", false))
                    } finally {
                        fixture.restoreStorage(storage)
                    }
                }
            } finally {
                android.edit().apply {
                    if (notice is Boolean) putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice)
                    else remove(IMPORTANT_NOTICE_SUPPRESSED)
                }.commit()
            }
        }
    }

    private fun envelope(native: JSONObject) = JSONObject()
        .put("app", "CRTicket").put("schemaVersion", 2)
        .put("contents", JSONArray(listOf("settings"))).put("tickets", JSONArray())
        .put("settings", JSONObject().put("native", native))

    private fun preservingChoices(block: () -> Unit) {
        val original = preferences.all
        try {
            block()
        } finally {
            preferences.edit().apply {
                for (key in listOf("previews", "automatic")) {
                    val value = original[key]
                    if (value is Boolean) putBoolean(key, value) else remove(key)
                }
            }.commit()
        }
    }
}
