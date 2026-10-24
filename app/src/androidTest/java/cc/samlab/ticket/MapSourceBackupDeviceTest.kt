package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import cc.samlab.ticket.ui.settings.BackupContent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapSourceBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun settingsExportImportRestoresMapAndRespectsExcludedOrOlderSettings() {
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val source = preferences.getString("mapSource", null)
        val notice = preferences.all[IMPORTANT_NOTICE_SUPPRESSED]
        WelcomeTestState(context).use {
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var activity: MainActivity
                    scenario.onActivity { activity = it }
                    val fixture = BackupDeviceFixture(compose, activity)
                    fixture.ready()
                    val originalStorage = fixture.storage()
                    try {
                        openMap()
                        compose.onNodeWithTag("map-source:france").performScrollTo().performClick()
                        pressBack()
                        fixture.evaluate("localStorage.setItem('mapSource', 'osm'); true")
                        val excluded = BackupContent.entries.filter { it != BackupContent.SETTINGS }
                            .map { context.getString(it.label) }.toSet()
                        val exported = fixture.export(excluded)
                        val settings = exported.getJSONObject("settings")
                        assertEquals("france", settings.getJSONObject("native").getString("mapSource"))
                        assertEquals("france", settings.getJSONObject("storage").getString("mapSource"))

                        val mapBackup = envelope(JSONObject().put("mapSource", "france"))
                        scenario.onActivity { preferences.edit().putString("mapSource", "hot").commit() }
                        assertTrue(fixture.import(mapBackup.toString(), settings = true).getBoolean("success"))
                        assertEquals("france", preferences.getString("mapSource", null))
                        assertEquals("\"france\"", fixture.evaluate("localStorage.getItem('mapSource')"))
                        openMap()
                        compose.onNodeWithTag("map-source:france").assertIsSelected()
                        pressBack()

                        scenario.onActivity { preferences.edit().putString("mapSource", "hot").commit() }
                        val withTickets = JSONObject(mapBackup.toString())
                            .put("contents", JSONArray(listOf("tickets", "settings")))
                        assertTrue(fixture.import(withTickets.toString(), settings = true,
                            excluded = setOf(context.getString(R.string.backup_content_settings)))
                            .getBoolean("success"))
                        assertEquals("hot", preferences.getString("mapSource", null))

                        val old = envelope(JSONObject().put("importantNoticeSuppressed", true))
                        assertTrue(fixture.import(old.toString(), settings = true).getBoolean("success"))
                        assertEquals("hot", preferences.getString("mapSource", null))
                        val invalid = envelope(JSONObject().put("mapSource", "invalid"))
                        assertFalse(fixture.import(invalid.toString(), settings = true).getBoolean("success"))
                        assertEquals("hot", preferences.getString("mapSource", null))
                    } finally {
                        fixture.restoreStorage(originalStorage)
                    }
                }
            } finally {
                preferences.edit().putString("mapSource", source).apply {
                    if (notice is Boolean) putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice)
                    else remove(IMPORTANT_NOTICE_SUPPRESSED)
                }.commit()
            }
        }
    }

    private fun openMap() {
        compose.onNode(hasText(context.getString(R.string.settings_map)) and hasClickAction())
            .performScrollTo().performClick()
    }

    private fun envelope(native: JSONObject) = JSONObject()
        .put("app", "CRTicket").put("schemaVersion", 2)
        .put("contents", JSONArray(listOf("settings"))).put("tickets", JSONArray())
        .put("settings", JSONObject().put("native", native))
}
