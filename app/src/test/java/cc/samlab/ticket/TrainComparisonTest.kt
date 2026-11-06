package cc.samlab.ticket

import cc.samlab.ticket.ui.comparison.*
import org.junit.Assert.*
import org.junit.Test

class TrainComparisonTest {
    @Test fun validatesCompleteNumbersAndEditablePrefixes() {
        listOf("1234", "Z1", "T9999", "K3", "N12", "V7777").forEach {
            assertTrue(it, validComparisonNumber(it))
            assertTrue(it, validComparisonInput(it))
        }
        listOf("", "Z", "123", "12345", "G1", "Z12345", "K-3", "V3/4").forEach {
            assertFalse(it, validComparisonNumber(it))
        }
        assertTrue(validComparisonInput(""))
        assertTrue(validComparisonInput("N"))
        assertFalse(validComparisonInput("V12345"))
        assertFalse(validComparisonInput("G1"))
        assertEquals("K3", normalizeComparisonNumber(" k3 "))
    }

    @Test fun sortsNewNumbersAndIndexesBothNumbersIncludingCollisions() {
        val data = TrainComparisonIndex(listOf(
            TrainNumberComparison("K3", "北京", "二连", "V3"),
            TrainNumberComparison("T10", "甲", "乙", "N10"),
            TrainNumberComparison("Z10", "甲", "乙", "Z2"),
            TrainNumberComparison("Z2", "乙", "甲", "Z10"),
            TrainNumberComparison("1234", "甲", "乙", "N2"),
        ))
        assertEquals(listOf("Z2", "Z10", "N2", "N10", "V3"), data.rows.map { it.new })
        assertEquals(listOf(4), data.find("K3"))
        assertEquals(data.find("K3"), data.find("V3"))
        assertEquals(listOf(0, 1), data.find("Z2"))
        assertEquals(listOf(2), data.find("1234"))
        assertTrue(data.find("V9999").isEmpty())
    }
}
