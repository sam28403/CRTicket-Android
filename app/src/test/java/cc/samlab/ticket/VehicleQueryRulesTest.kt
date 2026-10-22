package cc.samlab.ticket

import cc.samlab.ticket.ui.railfan.VehicleHistory
import cc.samlab.ticket.ui.railfan.VehicleSearch
import cc.samlab.ticket.ui.railfan.updateVehicleHistory
import cc.samlab.ticket.ui.railfan.vehicleSeriesTone
import cc.samlab.ticket.ui.railfan.VehicleSeriesTone
import cc.samlab.ticket.ui.railfan.vehicleImageModel
import cc.samlab.ticket.ui.railfan.specialVehicleImage
import org.junit.Assert.*
import org.junit.Test

class VehicleQueryRulesTest {
    @Test
    fun globalVehiclePreviewRecognizesFullVehicleNumbersWithoutMatchingTrainNumbers() {
        listOf("CR400BF-Z-0524", "CRH380B3626", "380B-3626", "1E1234", "CR200J4004").forEach {
            assertEquals("emu/$it", VehicleSearch.forVehicle(it)?.path)
        }
        assertEquals("emu/CRH380B3626", VehicleSearch.forVehicle(" crh380b3626 ")?.path)
        listOf("G1", "S1", "K1234", "Z1234", "1461", "CRH", "380B", "设置").forEach {
            assertNull(it, VehicleSearch.forVehicle(it))
        }
    }

    @Test
    fun globalAssignmentSearchRequiresACompleteCurrentTrainNumber() {
        listOf("G1", "D123", "C4699", "S1", "S9999").forEach {
            assertEquals("train/$it", VehicleSearch.forTrain(it)?.path)
        }
        assertEquals("train/G1", VehicleSearch.forTrain(" g1 ")?.path)
        listOf(
            "", "G", "D", "C", "S", "123", "G12345", "S12345", "G1/G2", "G 1",
            "K123", "Z99", "T1", "Y1", "L1", "1461", "50001", "N1", "CRH380B3626",
        ).forEach {
            assertNull(it, VehicleSearch.forTrain(it))
        }
        // This explicit train entry must not alter rail.re's normal fuzzy vehicle search rules.
        assertEquals("emu/K123", VehicleSearch.parse("K123")?.path)
    }
    @Test
    fun specialLiveriesOverrideBothColorAndArtworkOnlyForTheExactVehicle() {
        assertEquals(VehicleSeriesTone.PURPLE, vehicleSeriesTone("CR400BFZ0524"))
        assertEquals(VehicleSeriesTone.PURPLE, vehicleSeriesTone("CR400BF-Z-0524"))
        assertEquals(VehicleSeriesTone.SKY_BLUE, vehicleSeriesTone("CR400BF-C-5162"))
        assertEquals("/assets/imgs/Trains/Cute/CR400BF-Z-0524.png", specialVehicleImage("CR400BFZ-0524"))
        assertEquals("/assets/imgs/Trains/Cute/CR400BF-C-5162.png", specialVehicleImage("CR400BFC5162"))
        assertEquals(VehicleSeriesTone.ORANGE, vehicleSeriesTone("CR400BFZ0525"))
        assertEquals(VehicleSeriesTone.ORANGE, vehicleSeriesTone("CR400BFC5163"))
        assertNull(specialVehicleImage("CR400BFZ0525"))
        assertNull(specialVehicleImage("CR400BFC5163"))
        assertNotEquals(
            VehicleHistory("CR400BFZ0524", "G1", "2026-10-21 12:00").imageKey,
            VehicleHistory("CR400BFZ0525", "G1", "2026-10-21 12:00").imageKey,
        )
    }
    @Test
    fun badgesUseRequestedSeriesColorsIncludingVariantSuffixes() {
        val examples = mapOf(
            VehicleSeriesTone.RED to listOf("CR400AF-Z-2251", "CR400AFAS1234", "CR300AF-1001"),
            VehicleSeriesTone.ORANGE to listOf("CR400BFA-5066", "CR400BF-GZ-1001", "CR300BF-1001"),
            VehicleSeriesTone.OLIVE to listOf("YW25G-123", "RW25T-123", "YZ25G-123", "RZ25K-123",
                "SYW25B-123", "SYZ25B-123", "SRW25T-123", " syw-123 "),
            VehicleSeriesTone.GREEN to listOf("CR200J-1001", "CR200JC-1001", "CR200JS-G-1001"),
            VehicleSeriesTone.BLACK to listOf("CRH1A-1001", "CRH1E-1001"),
            VehicleSeriesTone.BLUE to listOf("CRH380A-1001", "CRH380AL-1001", "CRH2A-1001", "CR450AF-1001"),
        )
        examples.forEach { (tone, numbers) ->
            numbers.forEach { assertEquals(it, tone, vehicleSeriesTone(it)) }
        }
    }

    @Test
    fun cr200ImageAliasesUseTheRequestedModels() {
        assertEquals("CR200J1A", vehicleImageModel("CR200J"))
        assertEquals("CR200J1B", vehicleImageModel("CR200JB"))
        assertEquals("CR200J1C", vehicleImageModel("CR200JC"))
        assertEquals("CR200J1D", vehicleImageModel("CR200JD"))
        assertEquals("CR400BFA", vehicleImageModel("CR400BFA"))
    }
    @Test
    fun recentSearchesDeduplicateMoveToFrontAndKeepTen() {
        var history = emptyList<String>()
        (1..12).forEach { history = updateVehicleHistory(history, "G$it") }
        assertEquals(10, history.size)
        assertEquals("G12", history.first())
        assertFalse(history.contains("G1"))
        history = updateVehicleHistory(history, " g5 ")
        assertEquals("G5", history.first())
        assertEquals(1, history.count { it == "G5" })
        assertEquals(history, updateVehicleHistory(history, "../G1"))
    }
    @Test
    fun trainRulesMatchRailReIncludingInspectionServices() {
        listOf("G1", "D1234", "C99", "0G123").forEach {
            assertEquals("train/$it", VehicleSearch.parse(it)?.path)
        }
        assertEquals("train/G1", VehicleSearch.parse(" g1 ")?.path)
        listOf("G12345", "K1", "380B-3626", "BF5", "1E", "CR400AF+BF", "@SH").forEach {
            assertEquals("emu/$it", VehicleSearch.parse(it)?.path)
        }
    }

    @Test
    fun invalidInputCannotBecomeAUrlPath() {
        listOf("", " ", "G1/G2", "../G1", "G1?x=1", "北京南", "G 1").forEach {
            assertNull(VehicleSearch.parse(it))
        }
    }

    @Test
    fun modelLookupKeepsVariantAndFormatsSourceVehicleNumber() {
        val row = VehicleHistory("CR400BFA5066", "G1", "2026-10-21 11:24")
        assertEquals("CR400BFA-5066", row.displayVehicle)
        assertEquals("CR400BFA", row.modelKey)
        assertEquals("2026-10-21", row.day)
        assertEquals("11:24", row.time)
        assertEquals("CRH380B", row.copy(vehicle = "CRH380B3626").modelKey)
    }
}
