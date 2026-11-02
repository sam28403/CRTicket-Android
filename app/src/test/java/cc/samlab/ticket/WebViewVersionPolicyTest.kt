package cc.samlab.ticket

import cc.samlab.ticket.web.WebViewVersionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewVersionPolicyTest {
    @Test fun comparesNumericMajorVersionAtBoundary() {
        assertTrue(WebViewVersionPolicy.isUnsupported("99.0.4844.88"))
        assertTrue(WebViewVersionPolicy.isUnsupported("110.0.5481.154"))
        assertFalse(WebViewVersionPolicy.isUnsupported("111.0.5563.58"))
        assertFalse(WebViewVersionPolicy.isUnsupported("150.0.1.0"))
    }

    @Test fun unknownVersionDoesNotClaimToBeOutdated() {
        for (version in listOf(null, "", "unknown", "0.0.0.0")) {
            assertFalse(WebViewVersionPolicy.isUnsupported(version))
        }
    }
}
