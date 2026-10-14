package cc.samlab.ticket

import cc.samlab.ticket.ui.window.PaneBounds
import cc.samlab.ticket.ui.window.panePlan
import org.junit.Assert.*
import org.junit.Test

class PanePolicyTest {
    @Test fun tabletNavigationUsesOneThirdAndFoldUsesPhysicalBoundary() {
        val tablet = panePlan(2400, 1600, 2f, firstPaneFraction = 1f / 3f)
        assertEquals(776, tablet.first.right)
        val folded = panePlan(2400, 1600, 2f, PaneBounds(1180, 0, 1220, 1600),
            firstPaneFraction = 1f / 3f)
        assertEquals(1180, folded.first.right)
        assertEquals(1220, folded.second!!.left)
    }
    @Test fun respondsToWindowRatherThanPhysicalScreen() {
        assertNotNull(panePlan(2560, 1440, 2f).second)
        assertNull(panePlan(1600, 2400, 2f).second)
        assertNull(panePlan(1100, 1440, 2f).second)
        // Showing the IME must not tear down and recreate the focused search pane.
        assertNotNull(panePlan(2560, 600, 2f).second)
    }

    @Test fun verticalHingeAndZeroWidthCreaseAreExcluded() {
        for (size in listOf(0, 40)) {
            val hinge = PaneBounds(780, 0, 780 + size, 2400)
            val plan = panePlan(1600, 2400, 2f, hinge)
            assertEquals(780, plan.first.right)
            assertEquals(780 + size, plan.second!!.left)
        }
    }

    @Test fun tabletopFoldSplitsIntoTopAndBottomPanes() {
        val plan = panePlan(1600, 2200, 2f, PaneBounds(0, 1000, 1600, 1040), false)
        assertEquals(1000, plan.first.bottom)
        assertEquals(1040, plan.second!!.top)
    }

    @Test fun foldOutsideUsableAreaDoesNotCreateEmptyPanes() {
        assertNull(panePlan(1000, 1200, 2f, PaneBounds(0, -200, 1000, -160), false).second)
    }
}
