package cc.samlab.ticket.ui.settings

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Known instrumentation outputs, including screenshots left by older app builds. */
internal object StorageTestScreenshots {
    private val folders = setOf(
        "adaptive-window-scenes",
        "credits-scenes",
        "expressive-home-scenes",
        "expressive-settings-scenes",
        "expressive-train-list",
        "fare-preview",
        "full-backup-scenes",
        "generator-layout",
        "history-keyboard",
        "history-preview",
        "important-notice",
        "issue-report-scenes",
        "language-settings-scenes",
        "localized-home-scenes",
        "monitor-screenshots",
        "operating-calendar-verification",
        "performance",
        "permission-scenes",
        "pull-refresh-scenes",
        "quiz-share",
        "railfan-scenes",
        "speedometer-scenes",
        "station-delay-tests",
        "station-motion",
        "station-verification",
        "statistics-scenes",
        "tablet-native-pages",
        "tracking-chip-width",
        "tracking-icon-scenes",
        "tracking-station-scenes",
        "train-list-sorting",
        "train-query-label-fit",
        "train-query-verification",
        "visits-scenes",
        "welcome-animation",
        "welcome-scenes",
    )
    private val filenames = setOf(
        "backup-password.png",
        "train-query-discovery.png",
        "update-install-confirmation.png",
        "update-install-permission.png",
        "update-notes.png",
        "update-settings.png",
        "welcome-backup.png",
        "welcome-terms.png",
    )
    private val extensions = setOf("png", "jpg", "jpeg", "webp")

    fun files(context: Context): List<File> =
        context.getExternalFilesDirs(null).filterNotNull().flatMap(::files)

    internal fun files(root: File): List<File> = root.listFiles().orEmpty().flatMap { child ->
        when {
            Files.isSymbolicLink(child.toPath()) -> emptyList()
            child.isFile && child.name in filenames -> listOf(child)
            child.isDirectory && (child.name in folders || child.name.startsWith("aod-probe-")) ->
                images(child)
            else -> emptyList()
        }
    }

    private fun images(file: File): List<File> = when {
        Files.isSymbolicLink(file.toPath()) -> emptyList()
        file.isDirectory -> file.listFiles().orEmpty().flatMap(::images)
        file.isFile && file.extension.lowercase() in extensions -> listOf(file)
        else -> emptyList()
    }
}
