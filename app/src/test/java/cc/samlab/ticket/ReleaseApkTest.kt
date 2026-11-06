package cc.samlab.ticket

import cc.samlab.ticket.update.selectReleaseApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseApkTest {
    private val regular = "CRTicket-v1.0.0.apk"
    private val offline = "CRTicket-v1.0.0-offline.apk"

    @Test fun twoPackagesDefaultToRegularRegardlessOfOrder() {
        assertEquals(regular, select(listOf(regular, offline)))
        assertEquals(regular, select(listOf(offline, regular)))
        assertEquals(regular, select(listOf("CRTicket-OFFLINE.apk", regular)))
    }

    @Test fun offlineUniversalDoesNotOverrideRegularPackage() {
        assertEquals(regular, select(listOf("CRTicket-offline-universal.apk", regular)))
    }

    @Test fun universalAndSinglePackageFallbackRemainAvailable() {
        val universal = "CRTicket-universal.apk"
        assertEquals(universal, select(listOf(regular, universal, offline)))
        assertEquals(regular, select(listOf(regular)))
        assertEquals(offline, select(listOf(offline)))
    }

    @Test fun missingOrAmbiguousPackagesHaveNoDownload() {
        assertNull(select(emptyList()))
        assertNull(select(listOf("CRTicket-arm64.apk", "CRTicket-x86.apk", offline)))
    }

    private fun select(names: List<String>): String? = selectReleaseApk(names) { it }
}
