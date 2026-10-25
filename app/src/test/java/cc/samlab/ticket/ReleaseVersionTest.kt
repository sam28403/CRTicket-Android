package cc.samlab.ticket

import cc.samlab.ticket.update.ReleaseVersion
import cc.samlab.ticket.update.isReleaseDownload
import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    private fun version(value: String) = requireNotNull(ReleaseVersion.parse(value))

    @Test fun installedBuildSuffixDoesNotTriggerAnUpdateToTheSameVersion() {
        assertEquals(version("v0.9.5"), version("0.9.5-abcdef1-261025"))
        assertEquals(version("v0.9.5"), version("0.9.5-abcdef1-261025-ci"))
    }

    @Test fun numericVersionsAndPreviewIdentifiersAreOrderedCorrectly() {
        assertTrue(version("1.10.0") > version("1.9.0"))
        assertTrue(version("1.0.0-beta.10") > version("1.0.0-beta.2"))
        assertTrue(version("1.0.0") > version("1.0.0-rc.1"))
        assertTrue(version("1.1.0-beta.1") > version("1.0.0"))
        assertTrue(version("1.0.0-rc.1") > version("1.0.0-beta.9"))
        assertNull(ReleaseVersion.parse("unknown"))
        assertNull(ReleaseVersion.parse("1.0"))
    }

    @Test fun downloadsMustBeHttpsAssetsFromThisRepository() {
        assertTrue(isReleaseDownload(
            "https://github.com/sam28403/CRTicket-Android/releases/download/v1.0.0/CRTicket.apk",
        ))
        assertFalse(isReleaseDownload("http://github.com/sam28403/CRTicket-Android/releases/download/x/a.apk"))
        assertFalse(isReleaseDownload("https://example.com/sam28403/CRTicket-Android/releases/download/x/a.apk"))
        assertFalse(isReleaseDownload("https://github.com/sam28403/CRTicket/releases/download/x/a.apk"))
    }
}
