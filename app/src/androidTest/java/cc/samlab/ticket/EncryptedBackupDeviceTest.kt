package cc.samlab.ticket

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedBackupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun portableV1BackupRestoresAndWrongPasswordNeverWrites() {
        restoreBackup()
    }

    @Test fun largeDecryptedBackupCrossesNativeBridgeWithoutHanging() {
        val vector = JSONObject(instrumentation.context.assets
            .open("backup-encryption-v1.json").bufferedReader().use { it.readText() })
        val plain = JSONObject(vector.getString("plain"))
        plain.getJSONArray("contents").put("visits")
        val visits = org.json.JSONArray()
        repeat(8000) { index ->
            visits.put(JSONObject().put("station", "Large backup fixture station ".repeat(2) + index)
                .put("time", 1700000000000L + index).put("historical", false))
        }
        plain.getJSONObject("settings").put("native", JSONObject().put("stationVisits", visits))
        assertTrue(plain.toString().length > 500_000)
        val encrypted = encryptTestBackup(plain.toString(), vector.getString("password"))
        restoreBackup(encrypted, setOf("到访车站记录"))
    }

    private fun restoreBackup(input: String? = null, excluded: Set<String> = emptySet()) {
        val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = android.all[IMPORTANT_NOTICE_SUPPRESSED]
        val vector = JSONObject(instrumentation.context.assets
            .open("backup-encryption-v1.json").bufferedReader().use { it.readText() })
        val password = vector.getString("password")
        val transferred = File(context.getExternalFilesDir(null), "cross-device-backup.json")
        val source = input ?: if (transferred.isFile) transferred.readText()
            else vector.getJSONObject("encrypted").toString()
        android.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
        try {
            WelcomeTestState(context).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var activity: MainActivity
                    scenario.onActivity {
                        activity = it
                        it.setShowWhenLocked(true)
                        it.setTurnScreenOn(true)
                    }
                    val fixture = BackupDeviceFixture(compose, activity)
                    fixture.ready()
                    val original = fixture.storage()
                    val native = BackupPreferences(context).snapshot().toString()
                    try {
                        val result = fixture.import(source, settings = true, excluded = excluded, password = password) {
                            compose.onNodeWithTag("backup-password").performTextInput("wrong-password")
                            compose.onNodeWithTag("backup-password-submit").performClick()
                            compose.waitUntil(20_000) {
                                compose.onAllNodesWithText(context.getString(R.string.backup_crypto_failed))
                                    .fetchSemanticsNodes().isNotEmpty()
                            }
                            assertEquals(original.toString(), fixture.storage().toString())
                            assertEquals(native, BackupPreferences(context).snapshot().toString())
                            compose.onNodeWithText(context.getString(R.string.settings_start_import))
                                .assertDoesNotExist()
                        }
                        assertTrue(result.toString(), result.getBoolean("success"))
                        assertRestored(fixture)
                        val encrypted = fixture.export(
                            excluded = setOf("车票历史记录", "设置项", "到访车站记录",
                                "测速记录与轨迹", "车票生成草稿", "余票监控配置"),
                            password = password,
                        )
                        assertEquals("CRTicketEncryptedBackup", encrypted.getString("format"))
                        assertFalse(encrypted.toString().contains("Portable backup fixture"))
                        assertFalse(encrypted.has("user"))
                        assertFalse(encrypted.has("settings"))
                        File(context.getExternalFilesDir(null), "portable-backup.json")
                            .writeText(encrypted.toString())
                        fixture.restoreStorage(original)
                        assertTrue(fixture.import(encrypted.toString(), settings = true, password = password)
                            .getBoolean("success"))
                        assertRestored(fixture)
                    } finally {
                        fixture.restoreStorage(original)
                    }
                }
            }
        } finally {
            android.edit().apply {
                if (notice == null) remove(IMPORTANT_NOTICE_SUPPRESSED)
                else putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice as Boolean)
            }.commit()
            transferred.delete()
        }
    }

    private fun assertRestored(fixture: BackupDeviceFixture) {
        val state = fixture.storage()
        assertEquals("[\"G123\"]", state.getString("crticket-train-query-history"))
        assertEquals("Portable backup fixture", JSONObject(state.getString("user")).getString("username"))
    }
}
