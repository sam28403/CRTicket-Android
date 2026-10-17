package cc.samlab.ticket.ui.trainquery

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

internal data class GeoPoint(val longitude: Float, val latitude: Float)

internal fun readRouteOutline(assets: AssetManager): List<List<GeoPoint>> = buildList {
    for (file in listOf("china.json", "laos.json")) {
        val json = assets.open("web/maps/$file").bufferedReader().use { JSONObject(it.readText()) }
        val features = json.getJSONArray("features")
        for (index in 0 until features.length()) {
            val geometry = features.getJSONObject(index).optJSONObject("geometry") ?: continue
            val coordinates = geometry.optJSONArray("coordinates") ?: continue
            val polygons = if (geometry.optString("type") == "Polygon") JSONArray().put(coordinates) else coordinates
            for (polygon in 0 until polygons.length()) {
                val rings = polygons.getJSONArray(polygon)
                for (ring in 0 until rings.length()) {
                    val values = rings.getJSONArray(ring)
                    add(List(values.length()) {
                        val point = values.getJSONArray(it)
                        GeoPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                    })
                }
            }
        }
    }
}
