package cc.samlab.ticket.ui.settings

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Dedicated test output locations, including those used by older test runs. */
internal object StorageTestArtifacts {
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
        "native-appearance-tests",
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
        "cross-device-backup.json",
        "portable-backup.json",
        "station-delay-live.json",
        "train-code-probe.json",
        "train-query-discovery.png",
        "update-install-confirmation.png",
        "update-install-permission.png",
        "update-notes.png",
        "update-settings.png",
        "welcome-backup.png",
        "welcome-terms.png",
    )
    private val ciExternalFolders = setOf("llt-performance", "train-pdf")
    private val ciLogFolders = setOf(
        "benchmark-normal-smoke",
        "ci-timetable-performance",
        "full-suite-environment",
    )
    private val qaDirectorySuffixes = listOf("-indexeddb", "-prefs", "-local-storage")
    private val qaFileExtensions = setOf("tar", "xml")

    fun externalTargets(context: Context, ci: Boolean): List<File> =
        context.getExternalFilesDirs(null).filterNotNull().flatMap { externalTargets(it, ci) }

    internal fun externalTargets(root: File, ci: Boolean): List<File> {
        if (Files.isSymbolicLink(root.toPath())) return emptyList()
        val output = children(root).filter { child ->
            when {
                child.isFile -> child.name in filenames
                child.name in folders || child.name.startsWith("aod-probe-") ||
                    (ci && child.name in ciExternalFolders) -> hasFiles(child)
                else -> false
            }
        }
        if (!ci) return output
        val logs = children(File(root, "logs")).filter {
            it.isDirectory && it.name in ciLogFolders && hasFiles(it)
        }
        return output + logs
    }

    internal fun privateTargets(root: File): List<File> = children(root).filter { child ->
        when {
            child.isFile -> child.name == "monitor-process-restart-fixture.json" ||
                (child.name.startsWith("qa-") && child.extension in qaFileExtensions)
            child.isDirectory && child.name.startsWith("qa-") ->
                (child.name == "qa-after-indexeddb-paused" ||
                    qaDirectorySuffixes.any { child.name.endsWith(it) }) && hasFiles(child)
            else -> false
        }
    }

    private fun hasFiles(file: File): Boolean =
        file.isFile || children(file).any(::hasFiles)

    private fun children(directory: File): List<File> =
        if (Files.isSymbolicLink(directory.toPath())) emptyList()
        else directory.listFiles().orEmpty().filterNot { Files.isSymbolicLink(it.toPath()) }
}
