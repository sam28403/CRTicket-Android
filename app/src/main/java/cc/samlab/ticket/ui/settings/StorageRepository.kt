package cc.samlab.ticket.ui.settings

import android.content.Context
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.VehiclePhotoCache
import cc.samlab.ticket.diagnostics.ErrorReports
import java.io.File

internal enum class StorageKind(val title: Int) {
    WEB(R.string.storage_web),
    TIMETABLE(R.string.storage_timetable),
    LINES(R.string.storage_lines),
    VEHICLE_PHOTOS(R.string.storage_vehicle_photos),
    SHARES(R.string.storage_shares),
    EXPORTS(R.string.storage_exports),
    ERROR_LOGS(R.string.storage_error_logs),
    TEST_ARTIFACTS(R.string.storage_test_artifacts),
}

internal data class StorageSnapshot(
    val sizes: Map<StorageKind, Long> = emptyMap(),
    val savedBytes: Long = 0,
) {
    val cleanable: Long get() = sizes.values.sum()
}

internal class StorageRepository(private val context: Context) {
    private fun targets(kind: StorageKind): List<File> = when (kind) {
        StorageKind.TIMETABLE -> emptyList()
        StorageKind.ERROR_LOGS -> listOf(ErrorReports.directory(context))
        StorageKind.TEST_ARTIFACTS -> privateTestTargets() + externalTestTargets()
        StorageKind.WEB -> listOf(
            File(context.cacheDir, "WebView/Default/HTTP Cache"),
            File(context.cacheDir, "WebView/Default/Cache"),
            File(context.dataDir, "app_webview/Default/HTTP Cache"),
        )
        StorageKind.VEHICLE_PHOTOS -> listOf(VehiclePhotoCache.directory(context))
        StorageKind.LINES -> listOf(File(context.cacheDir, "railway-lines"))
        StorageKind.SHARES -> listOf(File(context.cacheDir, "quiz-share"))
        StorageKind.EXPORTS -> context.cacheDir.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith("export-") && it.extension == "tmp"
        }
    }

    fun scan(archiveBytes: Long): StorageSnapshot {
        val privateTests = privateTestTargets()
        val externalTests = externalTestTargets()
        val privateTestBytes = privateTests.sumOf(::size)
        val kinds = StorageKind.entries.filter {
            it != StorageKind.TEST_ARTIFACTS || privateTests.isNotEmpty() || externalTests.isNotEmpty()
        }
        return StorageSnapshot(
            kinds.associateWith { kind ->
                when (kind) {
                    StorageKind.TIMETABLE -> archiveBytes
                    StorageKind.TEST_ARTIFACTS -> privateTestBytes + externalTests.sumOf(::size)
                    else -> targets(kind).sumOf(::size)
                }
            },
            (size(context.filesDir) + size(File(context.dataDir, "shared_prefs")) +
                size(File(context.dataDir, "app_webview")) -
                size(File(context.dataDir, "app_webview/Default/HTTP Cache")) -
                size(ErrorReports.directory(context)) - privateTestBytes - archiveBytes).coerceAtLeast(0),
        )
    }

    private fun privateTestTargets(): List<File> =
        if (BuildConfig.CI_BUILD) StorageTestArtifacts.privateTargets(context.filesDir) else emptyList()

    private fun externalTestTargets(): List<File> =
        StorageTestArtifacts.externalTargets(context, BuildConfig.CI_BUILD)

    fun clear(kind: StorageKind) {
        require(kind != StorageKind.WEB && kind != StorageKind.TIMETABLE)
        if (kind == StorageKind.VEHICLE_PHOTOS) {
            VehiclePhotoCache.clear(context)
            return
        }
        targets(kind).forEach { target ->
            delete(target)
        }
    }

    private fun delete(file: File) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            check(file.delete())
            return
        }
        if (!file.exists()) return
        if (file.isDirectory) {
            checkNotNull(file.listFiles()).forEach(::delete)
        }
        check(file.delete())
    }

    private fun size(file: File): Long {
        if (!file.exists()) return 0
        // Do not follow symbolic links outside the app's storage.
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) return 0
        return if (file.isFile) file.length()
        else file.listFiles().orEmpty().sumOf(::size)
    }
}
