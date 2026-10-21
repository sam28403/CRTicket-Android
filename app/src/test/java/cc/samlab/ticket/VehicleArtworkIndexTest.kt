package cc.samlab.ticket

import cc.samlab.ticket.ui.railfan.VehicleArtworkIndex
import cc.samlab.ticket.ui.railfan.VehicleArtworkRule
import org.junit.Assert.*
import org.junit.Test

class VehicleArtworkIndexTest {
    private val index = VehicleArtworkIndex(listOf(
        VehicleArtworkRule(setOf("CRH380A"), listOf(251..259), "mtr"),
        VehicleArtworkRule(setOf("CRH1B"), listOf(1076..1080), "1e-head"),
        VehicleArtworkRule(setOf("CR200J", "CR200J3B"), listOf(4004..4005), "cross-border"),
    ))

    @Test
    fun exactPrefixesAndInclusiveFourDigitSerialsAreRequired() {
        assertEquals("mtr", index.resolve(" crh380a-0251 "))
        assertEquals("mtr", index.resolve("CRH380A0259"))
        assertNull(index.resolve("CRH380A0250"))
        assertNull(index.resolve("CRH380A0260"))
        assertNull(index.resolve("CRH380AL0251"))
        assertNull(index.resolve("CRH380A251"))
        assertNull(index.resolve("CRH380A00251"))
        assertNull(index.resolve("CRH380A0251+0252"))
    }

    @Test
    fun neighboringVehiclesAndOtherTypesKeepTheirDefaultArtwork() {
        assertEquals("1e-head", index.resolve("CRH1B1076"))
        assertEquals("1e-head", index.resolve("CRH1B1080"))
        assertNull(index.resolve("CRH1B1075"))
        assertNull(index.resolve("CRH1B1081"))
        assertNull(index.resolve("CRH1E1076"))
        assertEquals("cross-border", index.resolve("CR200J4004"))
        assertEquals("cross-border", index.resolve("CR200J3-B-4005"))
        assertNull(index.resolve("CR200J4003"))
        assertNull(index.resolve("CR200JC4004"))
    }
}
