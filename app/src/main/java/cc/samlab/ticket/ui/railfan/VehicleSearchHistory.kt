package cc.samlab.ticket.ui.railfan

import android.content.Context
import org.json.JSONArray

internal class VehicleSearchHistory(context: Context) {
    private val preferences = context.getSharedPreferences("vehicle-query", Context.MODE_PRIVATE)

    fun read(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).mapNotNull { VehicleSearch.parse(array.optString(it))?.keyword }
            .distinct().take(10)
    }.getOrDefault(emptyList())

    fun add(keyword: String): List<String> = updateVehicleHistory(read(), keyword).also {
        preferences.edit().putString("history", JSONArray(it).toString()).apply()
    }

    fun clear() {
        preferences.edit().remove("history").apply()
    }
}

internal fun updateVehicleHistory(history: List<String>, input: String): List<String> {
    val keyword = VehicleSearch.parse(input)?.keyword ?: return history
    return (listOf(keyword) + history).distinct().take(10)
}
