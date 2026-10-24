package cc.samlab.ticket

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.BackupContent
import cc.samlab.ticket.ui.settings.MapSource
import cc.samlab.ticket.ui.settings.availableBackupContents
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapBackupPreferencesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)

    @Test fun everySourceExportsAndRestoresAlongsideOtherAndroidSettings() = preservingPreferences {
        val backup = BackupPreferences(context)
        for (source in MapSource.entries) {
            preferences.edit().putString("mapSource", source.id).commit()
            assertEquals(source.id, backup.snapshot().getString("mapSource"))
            preferences.edit().putString("mapSource", "osm").commit()
            backup.restore(JSONObject().put("mapSource", source.id)
                .put("importantNoticeSuppressed", false))
            assertEquals(source.id, preferences.getString("mapSource", null))
            assertFalse(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, true))
        }
    }

    @Test fun invalidSourcesAreRejectedAndOlderBackupsKeepTheCurrentChoice() = preservingPreferences {
        val backup = BackupPreferences(context)
        preferences.edit().putString("mapSource", "topo")
            .putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        for (invalid in listOf("", "unknown", "https://example.com", 1, true,
            JSONObject.NULL, JSONObject(), JSONArray())) {
            assertTrue(runCatching {
                backup.restore(JSONObject().put("mapSource", invalid)
                    .put("importantNoticeSuppressed", false))
            }.isFailure)
            assertEquals("topo", preferences.getString("mapSource", null))
            assertTrue(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
        }
        backup.restore(JSONObject().put("importantNoticeSuppressed", false))
        assertEquals("topo", preferences.getString("mapSource", null))
    }

    @Test fun mapOnlyFilesExposeTheSettingsImportCategory() {
        for (section in listOf("native", "storage")) {
            val backup = JSONObject().put("app", "CRTicket").put("schemaVersion", 2)
                .put("contents", JSONArray(listOf("settings"))).put("tickets", JSONArray())
                .put("settings", JSONObject().put(section, JSONObject().put("mapSource", "hot")))
            assertEquals(setOf(BackupContent.SETTINGS), availableBackupContents(backup))
        }
    }

    private fun preservingPreferences(block: () -> Unit) {
        val source = preferences.getString("mapSource", null)
        val notice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED]
        try {
            block()
        } finally {
            preferences.edit().putString("mapSource", source).apply {
                if (notice is Boolean) putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice)
                else remove(IMPORTANT_NOTICE_SUPPRESSED)
            }.commit()
        }
    }
}
