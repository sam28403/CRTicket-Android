package cc.samlab.ticket

import cc.samlab.ticket.ui.railfan.VehiclePhotoParser
import org.junit.Assert.*
import org.junit.Test

class VehiclePhotoTest {
    private val html = """
        <div class="Item">
        <h1><a href="ProView.asp?ProId=42" title="CR400BF-Z-0524 京局">CR400BF-Z-<span>0524</span> 京局</a></h1>
        <img src="/xt/uploadfiles/CR400BF-Z-0524.jpg">
        拍摄日期：202408<BR>拍摄配属：宁局南宁车辆段<BR>
        拍摄线路：衡柳线<BR>拍摄城市：桂林<BR>拍摄车次：0<BR>
        拍摄作者：宁局的老虎<
    """.trimIndent()
    private val photo get() = VehiclePhotoParser.parse(html).single()

    @Test fun parsesLegacyHtmlAndKeepsMetadata() {
        assertEquals("http://www.xiaguanzhan.com/uploadfiles/CR400BF-Z-0524.jpg", photo.imageUrl)
        assertEquals("202408", photo.date)
        assertEquals("宁局南宁车辆段", photo.allocation)
        assertEquals("衡柳线", photo.line)
        assertEquals("桂林", photo.city)
        assertEquals("0", photo.train)
        assertEquals("宁局的老虎", photo.author)
        assertEquals("http://www.xiaguanzhan.com/ProView.asp?ProId=42", photo.sourceUrl)
    }

    @Test fun uniqueAbbreviationAndFullNumberWorkWithoutHistory() {
        assertEquals(photo, VehiclePhotoParser.select("400BFZ0524", emptyList(), listOf(photo)))
        assertEquals(photo, VehiclePhotoParser.select("CR400BF-Z-0524", emptyList(), listOf(photo)))
    }

    @Test fun ambiguousPhotosOrHistoryNeverSelectTheFirstVehicle() {
        val first = photo.copy(vehicle = "CR400AF-5033")
        val second = photo.copy(vehicle = "CR400BF-5033")
        assertNull(VehiclePhotoParser.select("5033", emptyList(), listOf(first, second)))
        assertNull(VehiclePhotoParser.select("5033", listOf("400AF5033", "400BF5033"), listOf(first)))
        assertNull(VehiclePhotoParser.select("5033", listOf("400AF5033+400BF5033"), listOf(first)))
    }

    @Test fun rejectsWrongVehicleAndExternalImages() {
        assertNull(VehiclePhotoParser.select("400BFZ0524", listOf("400BFZ0525"), listOf(photo)))
        assertNull(VehiclePhotoParser.select("400BFZ0525", emptyList(), listOf(photo)))
        assertTrue(VehiclePhotoParser.parse(html.replace("/xt/uploadfiles/", "http://other.test/")).isEmpty())
    }

    @Test fun buildsPreciseSearchesForCompleteVehicleNumbers() {
        assertEquals("CRH2A-2001", VehiclePhotoParser.searchKeyword("CRH2A-2001"))
        assertEquals("CR400BF-Z-0524", VehiclePhotoParser.searchKeyword("400BFZ0524"))
        assertNull(VehiclePhotoParser.searchKeyword("5033"))
        assertNull(VehiclePhotoParser.searchKeyword("BFZ"))
    }

    @Test fun cr400afaAliasesSearchAndMatchTheFullPhotoNumber() {
        val fullPhoto = photo.copy(vehicle = "CR400AF-A-1001")
        for (input in listOf("CR400AFA1001", "CR400AFA-1001", "CR400AF-A-1001")) {
            assertEquals("CR400AF-A-1001", VehiclePhotoParser.searchKeyword(input))
            assertEquals(fullPhoto, VehiclePhotoParser.select(
                input, listOf("CR400AFA-1001"), listOf(fullPhoto),
            ))
            assertEquals(fullPhoto, VehiclePhotoParser.select(input, emptyList(), listOf(fullPhoto)))
        }
        assertNull(VehiclePhotoParser.select(
            "CR400AFA1001", emptyList(), listOf(fullPhoto.copy(vehicle = "CR400AF-1001")),
        ))
    }

}
