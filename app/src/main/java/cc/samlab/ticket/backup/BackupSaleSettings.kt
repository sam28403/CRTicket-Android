package cc.samlab.ticket.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class BackupSaleSettings(context: Context) {
    private val mode = context.getSharedPreferences("sale-reminder-settings", Context.MODE_PRIVATE)
    private val query = context.getSharedPreferences("sale-time-query", Context.MODE_PRIVATE)

    fun snapshot(value: JSONObject): JSONObject = value
        .put("saleAlarmEnabled", mode.getBoolean("alarm", false))
        .put("saleSameCity", query.getBoolean("sameCity", true))
        .put("saleSearchHistory", JSONArray(query.getString("history", "[]")))

    fun validate(value: JSONObject) {
        for (key in listOf("saleAlarmEnabled", "saleSameCity")) {
            require(!value.has(key) || value.get(key) is Boolean) { "Invalid $key" }
        }
        if (value.has("saleSearchHistory")) {
            val rows = value.getJSONArray("saleSearchHistory")
            require(rows.length() <= 10)
            repeat(rows.length()) {
                val name = rows.get(it)
                require(name is String && name.isNotBlank() && name.length <= 100 &&
                    name.none { character -> character.code < 32 })
            }
        }
    }

    fun changes(value: JSONObject): Map<SharedPreferences, Map<String, Any?>> {
        val result = mutableMapOf<SharedPreferences, Map<String, Any?>>()
        if (value.has("saleAlarmEnabled")) result[mode] = mapOf("alarm" to value.getBoolean("saleAlarmEnabled"))
        val fields = mutableMapOf<String, Any?>()
        if (value.has("saleSameCity")) fields["sameCity"] = value.getBoolean("saleSameCity")
        if (value.has("saleSearchHistory")) fields["history"] = value.getJSONArray("saleSearchHistory").toString()
        if (fields.isNotEmpty()) result[query] = fields
        return result
    }
}
