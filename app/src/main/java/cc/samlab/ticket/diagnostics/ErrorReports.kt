package cc.samlab.ticket.diagnostics

import android.content.Context
import android.os.Build
import cc.samlab.ticket.BuildConfig
import java.io.File

internal object ErrorReports {
    fun directory(context: Context) = File(context.filesDir, "error-reports")

    fun file(context: Context) = File(directory(context), "latest.txt")

    @Synchronized
    fun save(context: Context, kind: String, thread: Thread, error: Throwable?) {
        val report = buildString {
            appendLine("${context.getString(cc.samlab.ticket.R.string.home_title)} " +
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Time: ${java.time.Instant.now()}")
            appendLine("Type: $kind")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(error?.stackTraceToString() ?: thread.stackTrace.joinToString("\n"))
        }
        file(context).apply { parentFile?.mkdirs() }.writeText(report.take(512_000))
    }
}
