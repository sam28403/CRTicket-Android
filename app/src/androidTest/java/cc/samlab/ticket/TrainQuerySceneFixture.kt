package cc.samlab.ticket

import android.content.Context
import cc.samlab.ticket.ui.trainquery.routeFeaturePolygons
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal fun currentTrainQueryFixture(): JSONObject {
    val zone = ZoneId.of("Asia/Shanghai")
    val now = ZonedDateTime.now(zone)
    val date = LocalDate.now(zone).minusDays(1)
    fun stop(name: String, index: Int, arrival: Long?, departure: Long?) = JSONObject().apply {
        put("no", "0${index + 1}")
        put("train", "G1")
        put("station", name)
        fun time(key: String, offset: Long?) {
            if (offset == null) { put(key, JSONObject.NULL); return }
            val value = now.plusMinutes(offset)
            put(key, value.toLocalTime().withSecond(0).withNano(0).toString())
            put("${key}Day", ChronoUnit.DAYS.between(date, value.toLocalDate()))
        }
        time("arrival", arrival)
        time("departure", departure)
        put("stay", if (index == 1) 10 else JSONObject.NULL)
        put("mileage", index * 600)
    }
    return JSONObject().apply {
        put("success", true)
        put("train", "G1")
        put("date", date.toString())
        put("from", "北京南")
        put("to", "上海虹桥")
        put("source", "路路通")
        put("stops", JSONArray().apply {
            put(stop("北京南", 0, null, -20))
            put(stop("南京南", 1, -5, 5))
            put(stop("上海虹桥", 2, 40, null))
        })
    }
}

internal fun verifyNativeRouteIslands(context: Context) {
    val json = context.assets.open("web/maps/china.json").bufferedReader().use { JSONObject(it.readText()) }
    val features = json.getJSONArray("features")
    val values = List(features.length()) { features.getJSONObject(it) }
    val hainan = values.first { it.getJSONObject("properties").getString("name") == "海南省" }
    assertEquals(1, routeFeaturePolygons(hainan).size)
    val island = routeFeaturePolygons(hainan).single().getJSONArray(0)
    assertTrue((0 until island.length()).all { island.getJSONArray(it).getDouble(1) > 18 })
    val guangdong = values.first { it.getJSONObject("properties").getString("name") == "广东省" }
    val original = guangdong.getJSONObject("geometry").getJSONArray("coordinates")
    val filtered = routeFeaturePolygons(guangdong)
    assertTrue(filtered.isNotEmpty())
    assertTrue(filtered.size < original.length())
    assertFalse(filtered.any { polygon ->
        val ring = polygon.getJSONArray(0)
        (0 until ring.length()).all {
            val point = ring.getJSONArray(it)
            point.getDouble(0) in 115.8..117.0 && point.getDouble(1) in 20.5..21.2
        }
    })
}
