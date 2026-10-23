package cc.samlab.ticket

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.samlab.ticket.ui.sale.nextSaleSlot
import java.time.Instant
import android.os.SystemClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleTimeRecommendationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun defaultCardsUseTheNextSaleSlot() {
        val fixture = TabletPageFixture(compose)
        val card = SemanticsMatcher("Sale time card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("sale-card:") == true
        }
        try {
            val started = SystemClock.elapsedRealtime()
            fixture.evaluate("window.crticketNavigate('/sale-time')")
            compose.waitUntil(60_000) {
                compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty()
            }
            println("SALE_RECOMMENDATION_READY_MS=${SystemClock.elapsedRealtime() - started}")
            val expected = nextSaleSlot(Instant.now()).toLocalTime().toString()
            val tags = compose.onAllNodes(card).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.TestTag]
            }
            tags.forEach { tag ->
                compose.onAllNodes(hasText(expected) and hasAnyAncestor(hasTestTag(tag)))
                    .assertCountEquals(1)
            }
            compose.onNodeWithText("起售时间", substring = false).assertDoesNotExist()
            fixture.capture("sale-time-next-slot")
        } catch (failure: Throwable) {
            fixture.capture("sale-time-next-slot-failure")
            throw failure
        } finally {
            fixture.close()
        }
    }
}
