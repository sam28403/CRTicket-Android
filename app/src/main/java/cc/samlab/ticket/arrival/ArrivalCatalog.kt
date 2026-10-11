package cc.samlab.ticket.arrival

import android.content.Context
import org.json.JSONArray

object ArrivalCatalog {
    private var cached: List<ArrivalStation>? = null

    @Synchronized fun stations(context: Context): List<ArrivalStation> {
        cached?.let { return it }
        val json = JSONArray(context.assets.open("arrival-stations.json")
            .bufferedReader().use { it.readText() })
        return (0 until json.length()).map { index ->
            val station = json.getJSONObject(index)
            ArrivalStation(station.getString("name"), station.getDouble("latitude"),
                station.getDouble("longitude"))
        }.also { cached = it }
    }
}
