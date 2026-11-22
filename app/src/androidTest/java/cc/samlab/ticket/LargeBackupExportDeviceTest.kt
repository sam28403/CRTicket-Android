package cc.samlab.ticket

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.speedometer.SpeedPoint
import cc.samlab.ticket.speedometer.SpeedRecording
import cc.samlab.ticket.speedometer.SpeedometerStore
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeBackupExportDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun savesLargePlainAndEncryptedBackups() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val notice = preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false)
        val plain = File.createTempFile("plain-backup-test-", ".json", context.cacheDir)
        val encrypted = File.createTempFile("encrypted-backup-test-", ".json", context.cacheDir)
        try {
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, true).commit()
            WelcomeTestState(context).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var activity: MainActivity
                    lateinit var previousHistory: List<SpeedRecording>
                    scenario.onActivity {
                        activity = it
                        it.setShowWhenLocked(true)
                        it.setTurnScreenOn(true)
                        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        SpeedometerStore.initialize(context)
                        previousHistory = SpeedometerStore.history
                        val track = SpeedRecording(
                            "large-export-fixture", "G1", "北京南", "上海虹桥", 1_790_000_000_000,
                            elapsed = 240_000_000, meters = 100_000.0,
                            points = List(240_000) { index ->
                                SpeedPoint(39.861224567, 116.370123456, index * 1000L, 0)
                            },
                        )
                        SpeedometerStore.history = listOf(track)
                    }
                    try {
                        val fixture = BackupDeviceFixture(compose, activity)
                        fixture.ready()
                        val excluded = setOf("车票历史记录", "个人资料", "设置项", "搜索记录",
                            "到访车站记录", "车票生成草稿", "余票监控配置")
                        fixture.exportTo(plain, excluded)
                        assertCompleteFile(plain, """"id": "large-export-fixture"""")
                        fixture.exportTo(encrypted, excluded, "large-backup-device-password")
                        assertCompleteFile(encrypted, """"format":"CRTicketEncryptedBackup"""")
                        assertTrue(encrypted.length() > plain.length())
                    } finally {
                        scenario.onActivity { SpeedometerStore.history = previousHistory }
                    }
                }
            }
        } finally {
            preferences.edit().putBoolean(IMPORTANT_NOTICE_SUPPRESSED, notice).commit()
            plain.delete()
            encrypted.delete()
        }
    }

    private fun assertCompleteFile(file: File, marker: String) {
        assertTrue("Saved ${file.length()} bytes", file.length() > 28L * 1024 * 1024)
        file.bufferedReader().use { reader ->
            val prefix = CharArray(4096)
            val count = reader.read(prefix)
            assertTrue(count > 0 && String(prefix, 0, count).contains(marker))
        }
        RandomAccessFile(file, "r").use {
            it.seek(it.length() - 1)
            assertEquals('}'.code, it.read())
        }
    }
}
