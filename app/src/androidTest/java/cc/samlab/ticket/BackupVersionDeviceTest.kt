package cc.samlab.ticket

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupVersionDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun versionedRestoreAndLegacyWebImports() {
        WelcomeTestState(context).use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity {
                    activity = it
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                    it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val fixture = BackupDeviceFixture(compose, activity)
                compose.waitUntil(20_000) {
                    runCatching {
                        compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                }
                fixture.ready()
                val storage = fixture.storage()
                try {
                    val excluded = cc.samlab.ticket.ui.settings.BackupContent.entries
                        .filter { it.id != "tickets" }.map { context.getString(it.label) }.toSet()
                    val exported = fixture.export(excluded)
                    assertEquals(BuildConfig.VERSION_NAME, exported.getString("appVersion"))
                    val source = JSONObject().put("app", "CRTicket").put("schemaVersion", 2)
                        .put("tickets", JSONArray()).put("contents", JSONArray(listOf("search")))
                        .put("settings", JSONObject().put("storage", JSONObject()
                            .put("crticket-train-query-history", "[\"G7\"]")))
                    val newer = JSONObject(source.toString()).put("appVersion", "999.0.0")
                    val result = fixture.import(newer.toString(), settings = true)
                    assertTrue(result.toString(), result.getBoolean("success"))
                    assertTrue(result.getString("message").contains(
                        context.getString(R.string.backup_update_recommended)))
                    val before = fixture.storage().toString()
                    newer.getJSONObject("settings").put("theme", "future-theme")
                    val rejected = fixture.import(newer.toString(), settings = true)
                    assertFalse(rejected.toString(), rejected.getBoolean("success"))
                    assertEquals(context.getString(R.string.backup_update_required), rejected.getString("message"))
                    assertEquals(before, fixture.storage().toString())
                    newer.getJSONObject("settings").remove("theme")
                    newer.getJSONObject("settings").put("native", JSONObject().put("language", "future-language"))
                    val nativeRejected = fixture.import(newer.toString(), settings = true)
                    assertFalse(nativeRejected.toString(), nativeRejected.getBoolean("success"))
                    assertEquals(context.getString(R.string.backup_update_required), nativeRejected.getString("message"))
                    assertEquals(before, fixture.storage().toString())
                    for (version in listOf<String?>(null, "0.1.0")) {
                        val legacy = JSONObject(source.toString())
                        if (version != null) legacy.put("appVersion", version)
                        val restored = fixture.import(legacy.toString(), settings = true)
                        assertTrue(restored.toString(), restored.getBoolean("success"))
                        assertFalse(restored.getString("message").contains(
                            context.getString(R.string.backup_update_recommended)))
                    }
                } finally {
                    fixture.restoreStorage(storage)
                }
            }
        }
    }
}
