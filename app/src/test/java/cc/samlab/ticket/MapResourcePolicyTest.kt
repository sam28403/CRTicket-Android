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
