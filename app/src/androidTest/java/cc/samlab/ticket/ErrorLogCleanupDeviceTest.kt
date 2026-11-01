package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.diagnostics.ErrorReports
import cc.samlab.ticket.ui.settings.StorageKind
import cc.samlab.ticket.ui.settings.StorageRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ErrorLogCleanupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun errorLogsCanBeCancelledClearedAndRecreatedWithoutDeletingSavedData() {
        val fixture = TabletPageFixture(compose)
        val context = fixture.activity
        val directory = ErrorReports.directory(context)
        val previous = directory.listFiles().orEmpty().filter { it.isFile }
            .associate { it.name to it.readBytes() }
        val saved = File(context.filesDir, "error-log-cleanup.keep")
        try {
            saved.writeText("retained")
            val repository = StorageRepository(context)
            val before = repository.scan(0).savedBytes
            ErrorReports.file(context).apply { parentFile!!.mkdirs() }.writeText("CI cleanup test report")
            assertTrue(repository.scan(0).sizes.getValue(StorageKind.ERROR_LOGS) > 0)
            assertEquals(before, repository.scan(0).savedBytes)
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_storage)) and hasClickAction())
                .performScrollTo().performClick()
            val row = compose.onNode(hasText(context.getString(R.string.storage_error_logs)) and hasClickAction())
            compose.waitUntil(10_000) {
                runCatching { row.assertIsEnabled() }.isSuccess
            }
            row.performScrollTo().performClick()
            pressBack()
            assertTrue(ErrorReports.file(context).exists())
            row.performScrollTo().performClick()
            compose.onNodeWithTag("storage-confirm").performClick()
            compose.waitUntil(10_000) { !directory.exists() }
            assertEquals(0L, repository.scan(0).sizes.getValue(StorageKind.ERROR_LOGS))
            assertEquals("retained", saved.readText())
            fixture.capture("storage-error-logs-cleared")
            ErrorReports.save(context, "CI recreation test", Thread.currentThread(), RuntimeException("test"))
            assertTrue(ErrorReports.file(context).readText().contains("CI recreation test"))
            repository.clear(StorageKind.ERROR_LOGS)
        } finally {
            previous.forEach { (name, bytes) ->
                File(directory, name).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
            }
            saved.delete()
            fixture.close()
        }
    }
}
