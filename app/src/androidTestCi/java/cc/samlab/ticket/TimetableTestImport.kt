package cc.samlab.ticket

import android.net.Uri
import androidx.compose.ui.test.junit4.ComposeTestRule
import cc.samlab.ticket.ui.settings.TimetableSettingsController
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal fun importAndSelectTimetable(
    fixture: TabletPageFixture,
    compose: ComposeTestRule,
    controller: TimetableSettingsController,
    source: File,
) {
    val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
        .joinToString("") { "%02x".format(it) }
    val date = ZipFile(source).use { zip ->
        zip.getInputStream(zip.getEntry("ver.txt")).bufferedReader().use { it.readText().trim() }
    }
    fixture.instrumentation.runOnMainSync { controller.importFile(Uri.fromFile(source)) }
    compose.waitUntil(60_000) { !controller.busy && controller.catalog.any { it.date == date } }
    val imported = controller.catalog.firstOrNull { it.id == "import:$date:$hash" }
        ?: controller.catalog.single { it.date == date }
    fixture.instrumentation.runOnMainSync { controller.select(imported.id) }
    compose.waitUntil(30_000) { !controller.busy && controller.activeId == imported.id }
}
