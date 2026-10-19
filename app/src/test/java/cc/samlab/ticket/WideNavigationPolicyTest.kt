package cc.samlab.ticket

import cc.samlab.ticket.ui.navigation.WideNavigationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WideNavigationPolicyTest {
    @Test fun switchingFeaturesStartsANewJourney() {
        listOf(
            "/", "/history", "/settings", "/about", "/user", "/monitor",
            "/list?from=BJP&to=SHH", "/train", "/station", "/train?train=",
        ).forEach { assertTrue(it, WideNavigationPolicy.startsJourney(it)) }
    }

    @Test fun queryLinksAndNestedEditorsPreserveTheirParent() {
        listOf(
            "/train?train=G1&date=2026-10-19&source=llt",
            "/train?date=2026-10-19&train=G3",
            "/station?station=%E5%8C%97%E4%BA%AC%E5%8D%97",
            "/settings/arrival", "/settings/permissions",
            "/history/editor", "/monitor/settings", "/about/report",
        ).forEach { assertFalse(it, WideNavigationPolicy.startsJourney(it)) }
    }

    @Test fun largeScreenStopsBeforeHomeButPhoneStillReturnsHome() {
        assertFalse(WideNavigationPolicy.canPop(true, "/home"))
        assertTrue(WideNavigationPolicy.canPop(false, "/home"))
        for (large in listOf(true, false)) {
            assertFalse(WideNavigationPolicy.canPop(large, null))
            assertTrue(WideNavigationPolicy.canPop(large, "/train?train=G1"))
            assertTrue(WideNavigationPolicy.canPop(large, "/station?station=北京南"))
            assertTrue(WideNavigationPolicy.canPop(large, "/settings"))
        }
    }
}
