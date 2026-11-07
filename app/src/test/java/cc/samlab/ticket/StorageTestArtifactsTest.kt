package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.StorageTestArtifacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageTestArtifactsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun selectsDedicatedExternalOutputsAndKeepsUnrelatedFiles() {
        val root = temporary.newFolder()
        val paths = listOf(
            "tablet-native-pages/nested/scene.png",
            "welcome-terms.png",
            "aod-probe-baseline/frame.png",
            "tablet-native-pages/test-backup.json",
            "tablet-native-pages/trace.txt",
            "logs/full-suite-environment/original.json",
            "logs/unrelated.log",
            "llt-performance/an.db",
            "train-code-probe.json",
            "personal/photo.png",
            "my-ticket.png",
        )
        paths.forEach { path ->
            File(root, path).apply {
                parentFile!!.mkdirs()
                writeText("fixture")
            }
        }
        val shared = setOf(
            "tablet-native-pages",
            "welcome-terms.png",
            "aod-probe-baseline",
            "train-code-probe.json",
        )
        assertEquals(shared, relativePaths(root, StorageTestArtifacts.externalTargets(root, ci = false)))
        assertEquals(
            shared + setOf("logs/full-suite-environment", "llt-performance"),
            relativePaths(root, StorageTestArtifacts.externalTargets(root, ci = true)),
        )
    }

    @Test fun emptyDirectoriesAreAbsentButAZeroByteArtifactCanStillBeCleaned() {
        val root = temporary.newFolder()
        val directory = File(root, "tablet-native-pages").apply { mkdirs() }
        assertTrue(StorageTestArtifacts.externalTargets(root, ci = false).isEmpty())
        File(directory, "empty.json").createNewFile()
        assertEquals(listOf(directory), StorageTestArtifacts.externalTargets(root, ci = false))
    }

    @Test fun selectsLegacyPrivateSnapshotsWithoutIncludingActiveAppData() {
        val root = temporary.newFolder()
        val snapshots = setOf(
            "qa-monitor-demo-after-indexeddb",
            "qa-post-monitor-crash-prefs",
            "qa-after-indexeddb-paused",
        )
        val archives = setOf("qa-monitor-demo-before.tar", "qa-original-data-versions.xml")
        snapshots.forEach { name ->
            File(root, name).mkdir()
            File(root, "$name/fixture").writeText("fixture")
        }
        (archives + setOf("speedometer.json", "personal-backup.tar", "qa-unrelated.json")).forEach { name ->
            File(root, name).writeText("fixture")
        }
        File(root, "daily-quiz").mkdir()
        File(root, "daily-quiz/record.json").writeText("fixture")
        assertEquals(snapshots + archives, relativePaths(root, StorageTestArtifacts.privateTargets(root)))
    }

    private fun relativePaths(root: File, files: List<File>): Set<String> =
        files.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
}
