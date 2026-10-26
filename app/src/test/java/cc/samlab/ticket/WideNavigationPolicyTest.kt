package cc.samlab.ticket

import cc.samlab.ticket.ui.navigation.WideNavigationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WideNavigationPolicyTest {
    @Test fun switchingFeaturesStartsANewJourney() {
        listOf(
            "/help", "/", "/history", "/settings", "/about", "/user", "/monitor",
            "/list?from=BJP&to=SHH", "/train", "/station", "/train?train=",
            "/railfan/lines", "/railfan/lines?line=",
        ).forEach { assertTrue(it, WideNavigationPolicy.startsJourney(it)) }
    }

    @Test fun queryLinksAndNestedEditorsPreserveTheirParent() {
        listOf(
            "/train?train=G1&date=2026-10-19&source=llt",
            "/train?date=2026-10-19&train=G3",
            "/station?station=%E5%8C%97%E4%BA%AC%E5%8D%97",
            "/railfan/lines?line=%E6%B2%AA%E6%98%86%E7%BA%BF",
            "/settings/arrival", "/settings/permissions", "/settings/language",
            "/help/tickets", "/help/backup?section=rules",
            "/history/editor", "/monitor/settings", "/about/report", "/about/credits", "/price/list",
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
