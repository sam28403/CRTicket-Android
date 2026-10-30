package cc.samlab.ticket

import cc.samlab.ticket.web.MapResourcePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapResourcePolicyTest {
    @Test fun gatewayOnlyMapsTheSelectedProviderAndFixedOrigin() {
        val origin = "https://appassets.androidplatform.net"
        assertEquals("https://openrailwaymap.app/style.json",
            MapResourcePolicy.railwayGateway("$origin/railway-map/openrailway/style.json", "openrailway"))
        assertNull(MapResourcePolicy.railwayGateway("$origin/railway-map/cnrail/style.json", "openrailway"))
        assertNull(MapResourcePolicy.railwayGateway("https://evil.example/railway-map/openrailway/style.json", "openrailway"))
        assertFalse(MapResourcePolicy.isRailwayResource("http://railmap.geogv.org.evil.example/style.json", "cnrail"))
    }

    @Test fun railwayResourcesStayOnTheOfficialHttpsOrigin() {
        assertTrue(MapResourcePolicy.isRailwayResource("https://openrailwaymap.app/style.json"))
        for (url in listOf(
            "http://openrailwaymap.app/style.json",
            "https://openrailwaymap.app.evil.example/style.json",
            "https://user:password@openrailwaymap.app/style.json",
            "https://openrailwaymap.app:8443/style.json",
        )) assertFalse(url, MapResourcePolicy.isRailwayResource(url))
    }

    @Test fun permitsHttpsRasterTiles() {
        assertTrue(MapResourcePolicy.isTile(
            "https://a.tile.openstreetmap.fr/hot/16/53900/24800.png"
        ))
        for (url in listOf(
            "https://a.tile.openstreetmap.fr/osmfr/16/53900/24800.png",
            "https://tile.openstreetmap.org/16/53900/24800.png",
            "https://a.tile.opentopomap.org/16/53900/24800.png",
        )) assertTrue(url, MapResourcePolicy.isTile(url))
    }

    @Test fun transportAllowsOnlyKnownHttpsProviders() {
        assertTrue(MapResourcePolicy.isTransportResource(
            "https://www.openstreetmap.org/export/embed.html?layer=transportmap",
        ))
        assertTrue(MapResourcePolicy.isTransportResource(
            "https://a.tile.thunderforest.com/transport/16/1/2.png?apikey=official-embed",
        ))
        for (url in listOf(
            "https://www.openstreetmap.org.evil.example/export/embed.html",
            "http://www.openstreetmap.org/export/embed.html",
            "https://user:password@www.openstreetmap.org/export/embed.html",
            "https://www.openstreetmap.org:8443/export/embed.html",
        )) assertFalse(url, MapResourcePolicy.isTransportResource(url))
    }

    @Test fun rejectsDocumentsScriptsAndOtherServers() {
        val urls = listOf(
            "https://www.openstreetmap.org/export/embed.html",
            "https://a.tile.openstreetmap.fr/hot/16/1/2.js",
            "https://a.tile.openstreetmap.fr/hot/16/1/2.png?callback=script",
            "https://a.tile.openstreetmap.fr/hot/16/1/2.png#fragment",
            "https://a.tile.openstreetmap.fr.evil.example/hot/16/1/2.png",
            "https://user:password@a.tile.openstreetmap.fr/hot/16/1/2.png",
            "https://a.tile.openstreetmap.fr:8443/hot/16/1/2.png",
            "http://a.tile.openstreetmap.fr/hot/16/1/2.png",
            "https://a.tile.openstreetmap.fr/hot/16/1/../../index.html"
        )
        for (url in urls) assertFalse(url, MapResourcePolicy.isTile(url))
    }
}
