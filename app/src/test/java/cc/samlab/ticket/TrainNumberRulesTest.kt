package cc.samlab.ticket

import cc.samlab.ticket.monitor.TicketMonitorMatches
import cc.samlab.ticket.railway.TrainNumberRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainNumberRulesTest : StringResourceTest() {
    @Test fun acceptsActiveNumberingRangesAndRejectsGaps() {
        for (train in listOf("G1", "g97", "1461", "50001", "C4699",
            "N1", "N1998", "n1998", "V1", "V5998", "V6001", "V6998", "V7001", "V7998", "v6501")) {
            assertEquals(train, "", TrainNumberRules.error(train))
        }
        for (train in listOf("N0", "N01", "N1999", "V0", "V01", "V5999", "V6000",
            "V6999", "V7000", "V7999", "X1", "G12345", "G1/G2", "G1B", " G1", "G1\n")) {
            assertEquals(train, "请输入正确的车次", TrainNumberRules.error(train))
        }
        assertEquals("请输入车次", TrainNumberRules.error(""))
    }

    @Test fun backgroundMonitorUsesTheSameOrdinaryTrainBoundariesAsTheWeb() {
        for (train in listOf("C1", "C999", "C4001", "C4699", " c4699 ", "D300", "S2999", "N1", "V6501")) {
            assertTrue(train, TicketMonitorMatches.ordinary(train))
        }
        for (train in listOf("C0", "C1000", "C4000", "C4700", "C4999", "C4699A", "G1", "X1")) {
            assertFalse(train, TicketMonitorMatches.ordinary(train))
        }
    }
}
