package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.VehicleArtworkOverrides
import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleQueryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleArtworkDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun verifiedRangesUseTheReviewedArtwork() {
        val rules = VehicleArtworkOverrides.load(context)
        val examples = mapOf(
            "CR200J4004" to "CR200JK", "CR200J4005" to "CR200JK",
            "CRH380A0251" to "CRH380M", "CRH380A0259" to "CRH380M",
            "CRH1B1076" to "CRH1E", "CRH1B1080" to "CRH1E",
            "CRH1E1233" to "CRH1A-A", "CRH2B2466" to "CRH2A",
            "CRH2B4105" to "CRH2A", "CRH2A2460" to "CRH2G",
            "CRH2E2461" to "CRH2G", "CRH2E2462" to "CRH2G",
            "CRH2E2463" to "CRH2E", "CRH5G5229" to "CRH5G",
            "CRH6A0624" to "CRH6A-B", "CRH6A4132" to "CRH6A-D",
            "CRH6A0218" to "CRH6A-CD", "CRH6AA0655" to "CRH6A-CD",
            "CRH6FA0673" to "CRH6A-B", "CRH6AA0220" to "CR200AFs1",
            "CRH6F0232" to "CRH6A-AZ", "CR200J2048" to "CR200J2",
            "CR200J3005" to "CR200J2", "CR200J1093" to "CR200J2",
            "CR400BFA0306" to "CR400BF-E", "CJ60701" to "CJ6s",
        )
        examples.forEach { (number, file) ->
            assertEquals(number, "/assets/imgs/Trains/Cute/$file.png", rules.resolve(number))
        }
        // Renumbered, rebuilt, open-ended, and adjacent numbers must retain their default version.
        listOf(
            "CRH380A0250", "CRH380A0260", "CRH1B1075", "CRH1B1081",
            "CR200JC4004", "CRH3A0302", "CRH3A0502", "CRH3AA0510",
            "CRH6AA0212", "CRH6AA0219", "CRH6AA0674", "CRH6AA0901",
            "CRH6FA0913", "CR400AF0207", "CR300AF0001", "CR200J1001",
        ).forEach { assertNull(numberMessage(it), rules.resolve(it)) }
    }

    @Test
    fun userSpecifiedExceptionImagesReallyLoadOnDevice() = runBlocking {
        listOf("CR200J4004", "CRH380A0251", "CRH1B1076").forEach { number ->
            val row = VehicleHistory(number, "", "")
            assertNotNull(number, VehicleQueryRepository.image(context, row.modelKey, number))
        }
    }

    private fun numberMessage(number: String) = "Unexpected override for $number"
}
