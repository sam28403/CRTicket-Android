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
            for (rings in routeFeaturePolygons(features.getJSONObject(index))) {
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

/** Match the web map: Hainan mainland only, no Dongsha inset or South China Sea inset. */
internal fun routeFeaturePolygons(feature: JSONObject): List<JSONArray> {
    val name = feature.optJSONObject("properties")?.optString("name")
    if (name == "南海诸岛") return emptyList()
    val geometry = feature.optJSONObject("geometry") ?: return emptyList()
    val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
    if (geometry.optString("type") == "Polygon") return listOf(coordinates)
    if (geometry.optString("type") != "MultiPolygon") return emptyList()
    val polygons = List(coordinates.length()) { coordinates.getJSONArray(it) }
    return when (name) {
        "海南省" -> listOfNotNull(polygons.maxByOrNull(::polygonArea))
        "广东省" -> polygons.filterNot { polygon ->
            val ring = polygon.getJSONArray(0)
            ring.length() > 0 && (0 until ring.length()).all { index ->
                val point = ring.getJSONArray(index)
                point.getDouble(0) in 115.8..117.0 && point.getDouble(1) in 20.5..21.2
            }
        }
        else -> polygons
    }
}

private fun polygonArea(polygon: JSONArray): Double {
    val ring = polygon.getJSONArray(0)
    var area = 0.0
    for (index in 0 until ring.length()) {
        val point = ring.getJSONArray(index)
        val next = ring.getJSONArray((index + 1) % ring.length())
        area += point.getDouble(0) * next.getDouble(1) - next.getDouble(0) * point.getDouble(1)
    }
    return kotlin.math.abs(area) / 2
}
