package cc.samlab.ticket

import cc.samlab.ticket.ui.settings.StorageTestScreenshots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageTestScreenshotsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun findsLegacyAndNestedScreenshotsWithoutIncludingBackupsOrUserImages() {
        val root = temporary.newFolder()
        val paths = listOf(
            "tablet-native-pages/nested/scene.png",
            "welcome-terms.png",
            "aod-probe-baseline/frame.png",
            "tablet-native-pages/backup.json",
            "tablet-native-pages/trace.txt",
            "personal/photo.png",
            "my-ticket.png",
        )
        paths.forEach { path ->
            File(root, path).apply {
                parentFile!!.mkdirs()
                writeText("fixture")
            }
        }
        assertEquals(
            setOf(paths[0], paths[1], paths[2]),
            StorageTestScreenshots.files(root).map { it.relativeTo(root).invariantSeparatorsPath }.toSet(),
        )
    }

    @Test fun emptyDirectoriesAreAbsentButAnEmptyScreenshotCanStillBeCleaned() {
        val root = temporary.newFolder()
        val directory = File(root, "tablet-native-pages").apply { mkdirs() }
        assertTrue(StorageTestScreenshots.files(root).isEmpty())
        val empty = File(directory, "empty.png").apply { createNewFile() }
        assertEquals(listOf(empty), StorageTestScreenshots.files(root))
    }
}
