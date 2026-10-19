package cc.samlab.ticket

import cc.samlab.ticket.monitor.TicketMonitorMatches
import cc.samlab.ticket.railway.TrainNumberRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainNumberRulesTest {
    @Test fun currentRulesStayActiveBeforeFutureNumberingChanges() {
        for (train in listOf("G1", "g97", "1461", "50001", "C4699")) {
            assertEquals(train, "", TrainNumberRules.error(train))
        }
        for (train in listOf("N1", "V1", "X1", "G12345", "G1/G2", "G1B", " G1", "G1\n")) {
            assertEquals(train, "请输入正确的车次", TrainNumberRules.error(train))
        }
        assertEquals("请输入车次", TrainNumberRules.error(""))
    }

    @Test fun backgroundMonitorUsesTheSameOrdinaryTrainBoundariesAsTheWeb() {
        for (train in listOf("C1", "C999", "C4001", "C4699", " c4699 ", "D300", "S2999")) {
            assertTrue(train, TicketMonitorMatches.ordinary(train))
        }
        for (train in listOf("C0", "C1000", "C4000", "C4700", "C4999", "C4699A", "G1", "N1")) {
            assertFalse(train, TicketMonitorMatches.ordinary(train))
        }
    }
}
