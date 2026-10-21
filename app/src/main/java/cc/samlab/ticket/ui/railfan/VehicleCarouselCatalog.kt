package cc.samlab.ticket.ui.railfan

import android.content.Context
import org.json.JSONObject

internal data class VehicleCarouselModel(val name: String, val image: String?)

internal fun readVehicleCarouselCatalog(context: Context): List<VehicleCarouselModel> {
    val data = context.assets.open("vehicle-carousel-models.json").bufferedReader().use {
        JSONObject(it.readText()).getJSONArray("models")
    }
    return List(data.length()) { index ->
        val row = data.getJSONObject(index)
        VehicleCarouselModel(row.getString("name"), row.optString("image").takeIf {
            !row.isNull("image") && it.isNotBlank()
        })
    }
}

internal fun vehicleImageModel(model: String): String = when (model) {
    "CR200J" -> "CR200J1A"
    "CR200JC" -> "CR200J1C"
    else -> model
}
