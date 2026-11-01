package cc.samlab.ticket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.VehiclePhotoCache
import cc.samlab.ticket.ui.settings.StorageRepository
import cc.samlab.ticket.ui.settings.StorageKind
import cc.samlab.ticket.ui.railfan.VehiclePhotoRepository
import cc.samlab.ticket.ui.railfan.VehicleSearch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehiclePhotoDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun photoWithoutHistoryAndUniqueAliasOpenDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = runBlocking {
            VehiclePhotoRepository.load(context, VehicleSearch("400BFZ0524", false), emptyList())
        }
        assertNotNull("Unique abbreviation must load even without history", photo)
        assertTrue(photo!!.image.width > 0)
        assertTrue(photo.info.vehicle.contains("0524"))
        assertTrue(photo.info.author.isNotBlank())
        assertNull(runBlocking {
            VehiclePhotoRepository.load(context, VehicleSearch("5033", false), emptyList())
        })
        val fixture = TabletPageFixture(compose)
        try {
            fixture.navigate("/railfan/vehicle")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("vehicle-search").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("vehicle-search").performTextReplacement("400BFZ0524")
            compose.onNodeWithTag("vehicle-search").performImeAction()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithTag("vehicle-photo").fetchSemanticsNodes().isNotEmpty()
            }
            fixture.capture("vehicle-photo-unique-alias")
            val wide = compose.onAllNodesWithTag("vehicle-photo-wide").fetchSemanticsNodes().isNotEmpty()
            if (wide) {
                val image = compose.onNodeWithContentDescription(photo.info.vehicle)
                    .fetchSemanticsNode().boundsInRoot
                val information = compose.onNodeWithTag("vehicle-photo-information")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("Information belongs beside the photo", image.right <= information.left)
                assertTrue("Wide photo must remain compact", image.height <= 290 * context.resources.displayMetrics.density)
            } else compose.onNodeWithTag("vehicle-photo").performClick()
            compose.onNodeWithText(photo.info.sourceUrl).assertExists()
            compose.onNodeWithText(photo.info.author, substring = true).assertExists()
            fixture.capture("vehicle-photo-details")
            assertTrue(StorageRepository(context).scan(0).sizes.getValue(StorageKind.VEHICLE_PHOTOS) > 0)
            val token = VehiclePhotoCache.token()
            StorageRepository(context).clear(StorageKind.VEHICLE_PHOTOS)
            assertEquals(0L, StorageRepository(context).scan(0).sizes.getValue(StorageKind.VEHICLE_PHOTOS))
            VehiclePhotoCache.write(context, photo.info.imageUrl, byteArrayOf(1), token)
            assertFalse("A download started before cleanup must not recreate the cache",
                VehiclePhotoCache.directory(context).exists())
        } finally {
            fixture.close()
        }
    }
}
