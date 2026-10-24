package cc.samlab.ticket

import cc.samlab.ticket.web.MapResourcePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapResourcePolicyTest {
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
