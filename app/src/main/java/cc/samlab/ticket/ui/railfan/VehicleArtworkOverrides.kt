package cc.samlab.ticket.ui.railfan

import android.content.Context
import org.json.JSONObject

internal object VehicleArtworkOverrides {
    @Volatile private var cached: VehicleArtworkIndex? = null

    fun load(context: Context): VehicleArtworkIndex {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: read(context).also { cached = it }
        }
    }

    private fun read(context: Context): VehicleArtworkIndex {
        val values = context.assets.open("vehicle-artwork-overrides.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("rules")
        }
        return VehicleArtworkIndex(List(values.length()) { index ->
            val row = values.getJSONObject(index)
            val prefixes = row.getJSONArray("prefixes")
            val ranges = row.getJSONArray("ranges")
            VehicleArtworkRule(
                prefixes = (0 until prefixes.length()).map { prefixes.getString(it) }.toSet(),
                ranges = List(ranges.length()) {
                    val range = ranges.getJSONArray(it)
                    range.getInt(0)..range.getInt(1)
                },
                image = row.getString("image"),
            )
        })
    }
}
