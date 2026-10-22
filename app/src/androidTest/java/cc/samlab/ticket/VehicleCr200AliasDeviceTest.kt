package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleQueryRepository
import cc.samlab.ticket.ui.railfan.vehicleImageModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleCr200AliasDeviceTest {
    @Test fun bAndDVariantsLoadTheirOwnCatalogArtwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("vehicle-model-images.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val examples = mapOf(
            "CR200JB2201" to "/assets/imgs/Trains/Cute/CR200J2.png",
            "CR200JD2201" to "/assets/imgs/Trains/Cute/CR200J-G.png",
        )
        for ((number, path) in examples) {
            val row = VehicleHistory(number, "", "")
            assertEquals(number, path, catalog.getString(vehicleImageModel(row.modelKey)))
            val expected = VehicleQueryRepository.imagePath(path)
            assertNotNull("Source artwork must load: $path", expected)
            val actual = VehicleQueryRepository.image(context, row.modelKey, number)
            assertSame("Wrong artwork for $number", expected, actual)
        }
    }
}
