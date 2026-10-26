package cc.samlab.ticket

import cc.samlab.ticket.ui.railfan.LineQueryParser
import cc.samlab.ticket.ui.railfan.matchingRailwayLines
import org.junit.Assert.*
import org.junit.Test

class LineQueryParserTest {
    @Test
    fun cumulativeMileagePreservesOrderAndUnknownValues() {
        val html = """
            <table><tr><td>线路名称</td><td>途经车站</td></tr>
            ${row("京沪线", "北京", "0km", "0km")}
            ${row("京沪线", "上海", "1,463km", "5km")}
            ${row("京沪线", "未知站", "", "3km")}
            ${row("京广线", "广州", "2,285km", "10km")}
            </table>
        """.trimIndent()
        val stops = LineQueryParser.stops(html, "京沪线")
        assertEquals(listOf("北京", "上海", "未知站"), stops.map { it.name })
        assertEquals(listOf("0", "1463", null), stops.map { it.kilometres })
    }

    @Test
    fun screenshotStationsUseCumulativeDistances() {
        val stations = listOf("商丘东" to "16", "芦庙" to "49", "亳州南" to "76", "古城东" to "106", "太和东" to "149")
        val html = stations.joinToString("") { (name, km) -> row("京港高速线商合段", name, "${km}km", "33km") }
        val result = LineQueryParser.stops(html, "京港高速线商合段")
        assertEquals(stations.map { it.first }, result.map { it.name })
        assertEquals(stations.map { it.second }, result.map { it.kilometres })
    }

    @Test
    fun catalogIgnoresOtherStationPickersAndSearchDoesNotLimitCandidates() {
        val html = """
            <select><option>北京</option></select>
            <datalist id="line"><option value=京沪线>京沪线</option>
            <option value='京广线'>京广线</option><option>京哈线</option>
            <option>京沪线</option><option>沪昆高速线</option></datalist>
        """.trimIndent()
        val lines = LineQueryParser.catalog(html)
        assertEquals(4, lines.size)
        assertEquals(listOf("京沪线", "京广线", "京哈线"), matchingRailwayLines(lines, " 京 "))
        assertEquals(listOf("沪昆高速线"), matchingRailwayLines(lines, "沪昆"))
        assertTrue(matchingRailwayLines(lines, "不存在").isEmpty())
        assertTrue(matchingRailwayLines(lines, " ").isEmpty())
        assertTrue(LineQueryParser.stops("<html>Service unavailable</html>", "京沪线").isEmpty())
    }

    private fun row(line: String, name: String, km: String, adjacent: String): String = """
        <tr class=tr_odd><td align=center>$line</td><td><a href=?statinfo=$name>$name</a></td>
        <td>CODE</td><td>TELE</td><td>123</td><td>北京</td><td>北京</td>
        <td align=center>$km</td><td>$adjacent</td><td>Yes</td></tr>
    """.trimIndent()
}
