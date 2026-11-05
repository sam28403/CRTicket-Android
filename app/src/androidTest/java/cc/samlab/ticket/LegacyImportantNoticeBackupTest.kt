package cc.samlab.ticket

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.backup.BackupPreferences
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyImportantNoticeBackupTest {
    @Test fun legacyPreferenceStillRoundTripsThroughBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val original = preferences.all[IMPORTANT_NOTICE_SUPPRESSED] as? Boolean
        val backup = BackupPreferences(context)
        try {
            backup.restore(JSONObject().put("importantNoticeSuppressed", true))
            assertTrue(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
            assertTrue(backup.snapshot().getBoolean("importantNoticeSuppressed"))

            backup.restore(JSONObject().put("importantNoticeSuppressed", false))
            assertFalse(preferences.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, true))
            assertFalse(backup.snapshot().getBoolean("importantNoticeSuppressed"))
        } finally {
            preferences.edit().apply {
                original?.let { putBoolean(IMPORTANT_NOTICE_SUPPRESSED, it) }
                    ?: remove(IMPORTANT_NOTICE_SUPPRESSED)
            }.commit()
        }
    }
}
