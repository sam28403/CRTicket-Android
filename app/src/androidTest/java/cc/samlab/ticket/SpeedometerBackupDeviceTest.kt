package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.speedometer.*
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedometerBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun settingsExportImportRoundTripPreservesTracksAndDrafts() {
        onMain { SpeedometerStore.initialize(context) }
        assumeTrue(SpeedometerStore.current == null)
        val history = SpeedometerStore.history
        val generator = context.getSharedPreferences("ticket-generator", Context.MODE_PRIVATE)
        val originalDraft = generator.getString("draft", null)
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val source = SpeedRecording(
            "backup-track-fixture", "G1", "北京南", "上海虹桥", 1_790_000_000_000,
            elapsed = 65_000, meters = 1111.95, vehicle = "YW25G-123456",
            points = listOf(SpeedPoint(39.86, 116.37, 1000, 0),
                SpeedPoint(39.87, 116.37, 2000, 0), SpeedPoint(39.88, 116.37, 3000, 2)),
        )
        try {
            onMain {
                preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
                generator.edit().putString("draft", JSONObject().put("trainNo", "G1")
                    .put("from", "北京南").put("message", "未完成草稿").toString()).commit()
                SpeedometerStore.history = listOf(source) + history
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity {
                    activity = it
                    it.setShowWhenLocked(true)
                    it.setTurnScreenOn(true)
                    it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val fixture = BackupDeviceFixture(compose, activity)
                fixture.ready()
                val storage = fixture.storage()
                try {
                    val exported = fixture.export()
                    val native = exported.getJSONObject("settings").getJSONObject("native")
                    val row = native.getJSONArray("speedRecordings").record(source.id)
                    assertEquals("YW25G-123456", row.getString("vehicle"))
                    assertEquals(2, row.getJSONArray("points").getJSONArray(2).getInt(3))
                    assertEquals("G1", native.getJSONObject("generatorDraft").getString("trainNo"))
                    val restoredNative = JSONObject()
                        .put("speedRecordings", JSONArray().put(row))
                        .put("generatorDraft", native.getJSONObject("generatorDraft"))
                    val payload = JSONObject(exported.toString()).put("tickets", JSONArray())
                        .put("settings", JSONObject().put("native", restoredNative))
                    val local = source.copy(id = "local-track-fixture", train = "G2")
                    onMain {
                        SpeedometerStore.history = listOf(local) + history
                        generator.edit().putString("draft", "{}").commit()
                    }
                    repeat(2) {
                        val result = fixture.import(payload.toString(), settings = true)
                        assertTrue(result.toString(), result.getBoolean("success"))
                        assertEquals(source, SpeedometerStore.history.single { it.id == source.id })
                        assertTrue(SpeedometerStore.history.contains(local))
                        assertEquals(history.size + 2, SpeedometerStore.history.size)
                        assertEquals("G1", JSONObject(generator.getString("draft", "")!!).getString("trainNo"))
                    }
                    val roundTrip = fixture.export().getJSONObject("settings").getJSONObject("native")
                    assertEquals(row.toString(), roundTrip.getJSONArray("speedRecordings").record(source.id).toString())
                    val before = fixture.storage().toString()
                    val beforeNative = BackupPreferences(context).snapshot().toString()
                    val bad = JSONObject(payload.toString())
                    bad.getJSONObject("settings").getJSONObject("native").getJSONArray("speedRecordings")
                        .getJSONObject(0).getJSONArray("points").getJSONArray(0).put(0, 91)
                    assertFalse(fixture.import(bad.toString(), settings = true).getBoolean("success"))
                    assertEquals(before, fixture.storage().toString())
                    assertEquals(beforeNative, BackupPreferences(context).snapshot().toString())
                    onMain {
                        val preferencesBackup = BackupPreferences(context)
                        for (invalid in listOf(
                            JSONObject().put("speedRecordings", JSONArray().put(JSONObject(row.toString()).put("elapsed", 59999))),
                            JSONObject().put("speedRecordings", JSONArray().put(JSONObject(row.toString()).put("started", "1"))),
                            JSONObject().put("generatorDraft", JSONObject().put("useCredit", 2)),
                        )) {
                            assertTrue(runCatching { preferencesBackup.restore(invalid) }.isFailure)
                        }
                        preferencesBackup.restore(JSONObject())
                    }
                    assertEquals(beforeNative, BackupPreferences(context).snapshot().toString())
                } finally {
                    fixture.restoreStorage(storage)
                }
            }
        } finally {
            onMain {
                SpeedometerStore.history = history
                SpeedometerStore.importHistory(context, JSONArray())
                generator.edit().apply {
                    if (originalDraft == null) remove("draft") else putString("draft", originalDraft)
                }.commit()
                preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
            }
        }
    }

    private fun JSONArray.record(id: String): JSONObject =
        (0 until length()).map { getJSONObject(it) }.single { it.getString("id") == id }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
}
