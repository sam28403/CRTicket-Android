package cc.samlab.ticket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.io.File
import org.junit.Assert.assertTrue

internal fun completeGeneratorExport(fixture: TabletPageFixture, format: String) {
    val extension = if (format == "pdf") "pdf" else "png"
    val output = File(fixture.activity.cacheDir, "generator-device-test.$extension")
    output.delete()
    try {
        // Deliver a real writable destination without interacting with a lock screen
        // that may cover DocumentsUI. The production file writer and JS callback run.
        fixture.instrumentation.runOnMainSync {
            val method = MainActivity::class.java.getDeclaredMethod(
                "onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java,
            ).apply { isAccessible = true }
            method.invoke(
                fixture.activity, 11, Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(output)),
            )
        }
        val deadline = System.currentTimeMillis() + 15_000
        while ((!output.exists() || output.length() == 0L) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertTrue("Export must write a nonempty $extension file", output.length() > 100)
        val bytes = output.readBytes()
        if (format == "pdf") assertTrue(String(bytes.take(4).toByteArray()) == "%PDF")
        else assertTrue(bytes.take(4) == listOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte()))
        val clearedDeadline = System.currentTimeMillis() + 10_000
        while (fixture.evaluate("JSON.parse(CRTicketAndroid.generatorDraft()).number") != "\"\"" &&
            System.currentTimeMillis() < clearedDeadline) {
            Thread.sleep(100)
        }
    } finally {
        output.delete()
    }
}
